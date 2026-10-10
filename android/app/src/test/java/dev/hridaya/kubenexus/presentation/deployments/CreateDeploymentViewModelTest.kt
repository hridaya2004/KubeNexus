package dev.hridaya.kubenexus.presentation.deployments

import androidx.lifecycle.viewModelScope
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.usecase.CreateDeploymentUseCase
import dev.hridaya.kubenexus.domain.usecase.CreateNamespaceUseCase
import dev.hridaya.kubenexus.presentation.testing.FakeNamespaceRepository
import dev.hridaya.kubenexus.presentation.testing.FakeResourceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateDeploymentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }


    private val resources = FakeResourceRepository()
    private val namespaces = FakeNamespaceRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vmTest(block: suspend TestScope.(CreateDeploymentViewModel) -> Unit) =
        runTest(testDispatcher) {
            val viewModel = CreateDeploymentViewModel(
                clusterId = "c-1",
                initialNamespace = "team-a",
                initialAvailableNamespaces = listOf("default", "team-a"),
                createDeploymentUseCase = CreateDeploymentUseCase(resources),
                createNamespaceUseCase = CreateNamespaceUseCase(namespaces),
                dispatcherProvider = testDispatcherProvider,
            )
            try {
                block(viewModel)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    private fun fillValidForm(viewModel: CreateDeploymentViewModel) {
        viewModel.onAction(CreateDeploymentUiAction.NameChanged("web-app"))
        viewModel.onAction(CreateDeploymentUiAction.ImageChanged("nginx:1.27"))
        viewModel.onAction(CreateDeploymentUiAction.ContainerPortChanged("8080"))
        // replicas keeps its "1" default and namespace its "team-a" initial value.
    }

    @Test
    fun `initial form state does not show required field errors before submit`() =
        vmTest { viewModel ->
            val state = viewModel.uiState.value
            assertTrue(state.fieldErrors.isEmpty())
            assertNull(state.errorMessage)

            viewModel.onAction(CreateDeploymentUiAction.PreviewSubmitted)
            val submittedState = viewModel.uiState.value
            assertTrue(submittedState.fieldErrors.containsKey("name"))
            assertTrue(submittedState.fieldErrors.containsKey("image"))
            assertNotNull(submittedState.errorMessage)
        }

    @Test
    fun `preview success moves to review step with generated yaml`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        assertTrue(viewModel.uiState.value.fieldErrors.isEmpty())

        viewModel.onAction(CreateDeploymentUiAction.PreviewSubmitted)

        val state = viewModel.uiState.value
        assertEquals(CreateDeploymentStep.REVIEW, state.step)
        assertNull(state.errorMessage)
        assertNotNull(state.generatedYaml)
        val yaml = state.generatedYaml!!
        assertTrue(yaml.contains("kind: Deployment"))
        assertTrue(yaml.contains("name: web-app"))
        assertTrue(yaml.contains("namespace: team-a"))
        assertTrue(yaml.contains("image: nginx:1.27"))
        assertTrue(yaml.contains("replicas: 1"))
        assertTrue(yaml.contains("containerPort: 8080"))
    }

    @Test
    fun `invalid draft surfaces field errors without clearing input`() = vmTest { viewModel ->
        viewModel.onAction(CreateDeploymentUiAction.NameChanged("Invalid_Name"))
        viewModel.onAction(CreateDeploymentUiAction.ImageChanged("nginx 1.27"))
        viewModel.onAction(CreateDeploymentUiAction.ReplicasChanged("9999"))
        viewModel.onAction(CreateDeploymentUiAction.ContainerPortChanged("abc"))

        val state = viewModel.uiState.value
        assertEquals(
            setOf("name", "image", "replicas", "containerPort"),
            state.fieldErrors.keys,
        )
        // Every keystroke survived validation - input is never discarded.
        assertEquals("Invalid_Name", state.name)
        assertEquals("nginx 1.27", state.image)
        assertEquals("9999", state.replicas)
        assertEquals("abc", state.containerPort)
        assertEquals("team-a", state.namespace)

        // Preview is blocked while the draft is invalid.
        viewModel.onAction(CreateDeploymentUiAction.PreviewSubmitted)
        assertEquals(CreateDeploymentStep.FORM, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.generatedYaml)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Errors clear live as the user fixes the input.
        viewModel.onAction(CreateDeploymentUiAction.NameChanged("web-app"))
        assertFalse(viewModel.uiState.value.fieldErrors.containsKey("name"))
        viewModel.onAction(CreateDeploymentUiAction.ReplicasChanged("3"))
        assertFalse(viewModel.uiState.value.fieldErrors.containsKey("replicas"))
    }

    @Test
    fun `apply success emits created effect and applies the reviewed yaml`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreateDeploymentUiAction.PreviewSubmitted)
        val reviewedYaml = viewModel.uiState.value.reviewedYaml
        assertNotNull(reviewedYaml)

        val effects = mutableListOf<CreateDeploymentUiEffect>()
        val collector = launch { viewModel.effects.collect { effects += it } }
        runCurrent()

        viewModel.onAction(CreateDeploymentUiAction.ApplySubmitted)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(listOf(CreateDeploymentUiEffect.Created("web-app")), effects)
        assertEquals(1, resources.createCalls.size)
        val (clusterId, manifest) = resources.createCalls.single()
        assertEquals("c-1", clusterId)
        assertEquals(reviewedYaml, manifest)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(CreateDeploymentStep.FORM, viewModel.uiState.value.step)
        assertEquals("", viewModel.uiState.value.name)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `reset action clears form inputs to initial state`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreateDeploymentUiAction.Reset)
        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals("", state.image)
        assertEquals(CreateDeploymentStep.FORM, state.step)
    }

    @Test
    fun `edited yaml is applied verbatim without regeneration`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreateDeploymentUiAction.PreviewSubmitted)
        val original = viewModel.uiState.value.reviewedYaml
        val edited = "$original# tuned by hand"
        viewModel.onAction(CreateDeploymentUiAction.ReviewedYamlChanged(edited))

        viewModel.onAction(CreateDeploymentUiAction.ApplySubmitted)
        advanceUntilIdle()

        val (_, manifest) = resources.createCalls.single()
        assertEquals(edited, manifest)
        assertFalse(manifest == original)
    }

    @Test
    fun `apply is blocked when the editor content is blank`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreateDeploymentUiAction.PreviewSubmitted)
        viewModel.onAction(CreateDeploymentUiAction.ReviewedYamlChanged("   \n  "))

        viewModel.onAction(CreateDeploymentUiAction.ApplySubmitted)
        advanceUntilIdle()

        assertTrue(resources.createCalls.isEmpty())
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `apply failure keeps review step edits and shows friendly error`() = vmTest { viewModel ->
        resources.createResult =
            Result.Error(AppError.Unknown(message = "forbidden: deployments is forbidden"))
        fillValidForm(viewModel)
        viewModel.onAction(CreateDeploymentUiAction.PreviewSubmitted)
        val edited = viewModel.uiState.value.reviewedYaml + "\n# keep this edit"
        viewModel.onAction(CreateDeploymentUiAction.ReviewedYamlChanged(edited))

        viewModel.onAction(CreateDeploymentUiAction.ApplySubmitted)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Still on review with every edit preserved for another attempt.
        assertEquals(CreateDeploymentStep.REVIEW, state.step)
        assertEquals(edited, state.reviewedYaml)
        assertEquals("web-app", state.name)
        assertEquals("nginx:1.27", state.image)
        assertEquals("8080", state.containerPort)
        assertEquals("team-a", state.namespace)
        assertFalse(state.isSubmitting)
        assertEquals(
            "forbidden: deployments is forbidden",
            state.errorMessage,
        )
    }

    @Test
    fun `service selection and port are included in preview yaml`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreateDeploymentUiAction.ServiceTypeSelected("NodePort"))
        viewModel.onAction(CreateDeploymentUiAction.ServicePortChanged("9090"))

        viewModel.onAction(CreateDeploymentUiAction.PreviewSubmitted)

        val state = viewModel.uiState.value
        assertEquals(CreateDeploymentStep.REVIEW, state.step)
        assertNotNull(state.generatedYaml)
        val yaml = state.generatedYaml!!.replace("'", "")
        assertTrue(yaml.contains("kind: Deployment"))
        assertTrue(yaml.contains("kind: Service"))
        assertTrue(yaml.contains("type: NodePort"))
        assertTrue(yaml.contains("port: 9090"))
        assertTrue(yaml.contains("targetPort: 8080"))
    }

    @Test
    fun `new namespace name validates live`() = vmTest { viewModel ->
        viewModel.onAction(CreateDeploymentUiAction.NewNamespaceNameChanged("Team_A"))
        assertEquals(
            "Use lowercase letters, numbers, and hyphens. It must start and end with a letter or number.",
            viewModel.uiState.value.newNamespaceError,
        )

        viewModel.onAction(CreateDeploymentUiAction.NewNamespaceNameChanged("-leading"))
        assertNotNull(viewModel.uiState.value.newNamespaceError)

        viewModel.onAction(CreateDeploymentUiAction.NewNamespaceNameChanged("a".repeat(64)))
        assertEquals("Must be 63 characters or fewer", viewModel.uiState.value.newNamespaceError)

        viewModel.onAction(CreateDeploymentUiAction.NewNamespaceNameChanged("team-b"))
        assertNull(viewModel.uiState.value.newNamespaceError)
    }

    @Test
    fun `namespace creation success appends selects and emits effect`() = vmTest { viewModel ->
        viewModel.onAction(CreateDeploymentUiAction.CreateNamespaceClicked)
        assertTrue(viewModel.uiState.value.showCreateNamespaceDialog)
        viewModel.onAction(CreateDeploymentUiAction.NewNamespaceNameChanged("team-b"))

        val effects = mutableListOf<CreateDeploymentUiEffect>()
        val collector = launch { viewModel.effects.collect { effects += it } }
        runCurrent()

        viewModel.onAction(CreateDeploymentUiAction.CreateNamespaceSubmitted)
        advanceUntilIdle()
        collector.cancel()

        val state = viewModel.uiState.value
        assertFalse(state.showCreateNamespaceDialog)
        assertFalse(state.isCreatingNamespace)
        assertEquals(listOf("default", "team-a", "team-b"), state.availableNamespaces)
        assertEquals("team-b", state.namespace)
        assertEquals(listOf(CreateDeploymentUiEffect.NamespaceCreated), effects)
        assertEquals(listOf("c-1" to "team-b"), namespaces.created)
    }

    @Test
    fun `namespace creation failure keeps dialog input and shows friendly message`() =
        vmTest { viewModel ->
            namespaces.createResult = Result.Error(AppError.Unknown(message = "already exists"))
            viewModel.onAction(CreateDeploymentUiAction.CreateNamespaceClicked)
            viewModel.onAction(CreateDeploymentUiAction.NewNamespaceNameChanged("team-b"))

            viewModel.onAction(CreateDeploymentUiAction.CreateNamespaceSubmitted)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.showCreateNamespaceDialog)
            assertEquals("team-b", state.newNamespaceName)
            assertFalse(state.isCreatingNamespace)
            assertEquals(
                "Couldn't create that namespace. The name may already be taken.",
                state.newNamespaceError,
            )
            assertFalse(state.availableNamespaces.contains("team-b"))
        }
}
