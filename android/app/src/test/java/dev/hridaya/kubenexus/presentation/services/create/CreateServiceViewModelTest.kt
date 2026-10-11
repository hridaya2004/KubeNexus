package dev.hridaya.kubenexus.presentation.services.create

import androidx.lifecycle.viewModelScope
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.usecase.CreateNamespaceUseCase
import dev.hridaya.kubenexus.domain.usecase.CreateServiceUseCase
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
class CreateServiceViewModelTest {

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

    private fun vmTest(block: suspend TestScope.(CreateServiceViewModel) -> Unit) =
        runTest(testDispatcher) {
            val viewModel = CreateServiceViewModel(
                clusterId = "c-1",
                initialNamespace = "team-a",
                initialAvailableNamespaces = listOf("default", "team-a"),
                createServiceUseCase = CreateServiceUseCase(resources),
                createNamespaceUseCase = CreateNamespaceUseCase(namespaces),
                dispatcherProvider = testDispatcherProvider,
            )
            try {
                block(viewModel)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    private fun fillValidForm(viewModel: CreateServiceViewModel) {
        viewModel.onAction(CreateServiceUiAction.NameChanged("web-app"))
        viewModel.onAction(CreateServiceUiAction.SelectorAppChanged("web-app"))
        viewModel.onAction(CreateServiceUiAction.PortChanged("80"))
        viewModel.onAction(CreateServiceUiAction.TargetPortChanged("8080"))
        // namespace keeps its "team-a" initial value.
    }

    @Test
    fun `initial form state does not show required field errors before submit`() =
        vmTest { viewModel ->
            val state = viewModel.uiState.value
            assertTrue(state.fieldErrors.isEmpty())
            assertNull(state.errorMessage)

            viewModel.onAction(CreateServiceUiAction.PreviewSubmitted)
            val submittedState = viewModel.uiState.value
            assertTrue(submittedState.fieldErrors.containsKey("name"))
            assertTrue(submittedState.fieldErrors.containsKey("selectorApp"))
            assertTrue(submittedState.fieldErrors.containsKey("port"))
            assertTrue(submittedState.fieldErrors.containsKey("targetPort"))
            assertNotNull(submittedState.errorMessage)
        }

    @Test
    fun `preview success moves to review step with generated service yaml`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        assertTrue(viewModel.uiState.value.fieldErrors.isEmpty())

        viewModel.onAction(CreateServiceUiAction.PreviewSubmitted)

        val state = viewModel.uiState.value
        assertEquals(CreateServiceStep.REVIEW, state.step)
        assertNull(state.errorMessage)
        assertNotNull(state.generatedYaml)
        val yaml = state.generatedYaml!!
        assertTrue(yaml.contains("kind: Service"))
        assertTrue(yaml.contains("name: web-app"))
        assertTrue(yaml.contains("namespace: team-a"))
        assertTrue(yaml.contains("app: web-app"))
        assertTrue(yaml.contains("type: ClusterIP"))
        assertTrue(yaml.contains("port: 80"))
        assertTrue(yaml.contains("targetPort: 8080"))
    }

    @Test
    fun `service type selection updates state and flows into the preview`() = vmTest { viewModel ->
        fillValidForm(viewModel)

        viewModel.onAction(CreateServiceUiAction.ServiceTypeSelected("NodePort"))

        assertEquals("NodePort", viewModel.uiState.value.serviceType)

        viewModel.onAction(CreateServiceUiAction.PreviewSubmitted)

        val yaml = viewModel.uiState.value.generatedYaml!!
        assertTrue(yaml.contains("type: NodePort"))
    }

    @Test
    fun `invalid draft surfaces field errors without clearing input`() = vmTest { viewModel ->
        viewModel.onAction(CreateServiceUiAction.NameChanged("Invalid_Name"))
        viewModel.onAction(CreateServiceUiAction.SelectorAppChanged("web app"))
        viewModel.onAction(CreateServiceUiAction.PortChanged("abc"))
        viewModel.onAction(CreateServiceUiAction.TargetPortChanged("def"))

        val state = viewModel.uiState.value
        assertTrue(state.fieldErrors.containsKey("name"))
        assertTrue(state.fieldErrors.containsKey("port"))
        assertTrue(state.fieldErrors.containsKey("targetPort"))
        // Every keystroke survived validation - input is never discarded.
        assertEquals("Invalid_Name", state.name)
        assertEquals("web app", state.selectorApp)
        assertEquals("abc", state.port)
        assertEquals("def", state.targetPort)
        assertEquals("team-a", state.namespace)

        // Preview is blocked while the draft is invalid.
        viewModel.onAction(CreateServiceUiAction.PreviewSubmitted)
        assertEquals(CreateServiceStep.FORM, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.generatedYaml)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Errors clear live as the user fixes the input.
        viewModel.onAction(CreateServiceUiAction.NameChanged("web-app"))
        assertFalse(viewModel.uiState.value.fieldErrors.containsKey("name"))
        viewModel.onAction(CreateServiceUiAction.PortChanged("80"))
        assertFalse(viewModel.uiState.value.fieldErrors.containsKey("port"))
        viewModel.onAction(CreateServiceUiAction.TargetPortChanged("8080"))
        assertFalse(viewModel.uiState.value.fieldErrors.containsKey("targetPort"))
    }

    @Test
    fun `apply success emits created effect and applies the reviewed yaml`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreateServiceUiAction.PreviewSubmitted)
        val reviewedYaml = viewModel.uiState.value.reviewedYaml
        assertNotNull(reviewedYaml)

        val effects = mutableListOf<CreateServiceUiEffect>()
        val collector = launch { viewModel.effects.collect { effects += it } }
        runCurrent()

        viewModel.onAction(CreateServiceUiAction.ApplySubmitted)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(listOf(CreateServiceUiEffect.Created("web-app")), effects)
        assertEquals(1, resources.createCalls.size)
        val (clusterId, manifest) = resources.createCalls.single()
        assertEquals("c-1", clusterId)
        assertEquals(reviewedYaml, manifest)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(CreateServiceStep.FORM, viewModel.uiState.value.step)
        assertEquals("", viewModel.uiState.value.name)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `reset action clears form inputs to initial state`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreateServiceUiAction.Reset)
        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals("", state.port)
        assertEquals(CreateServiceStep.FORM, state.step)
    }

    @Test
    fun `edited yaml is applied verbatim without regeneration`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreateServiceUiAction.PreviewSubmitted)
        val original = viewModel.uiState.value.reviewedYaml
        val edited = "$original# tuned by hand"
        viewModel.onAction(CreateServiceUiAction.ReviewedYamlChanged(edited))

        viewModel.onAction(CreateServiceUiAction.ApplySubmitted)
        advanceUntilIdle()

        val (_, manifest) = resources.createCalls.single()
        assertEquals(edited, manifest)
        assertFalse(manifest == original)
    }

    @Test
    fun `apply is blocked when the editor content is blank`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreateServiceUiAction.PreviewSubmitted)
        viewModel.onAction(CreateServiceUiAction.ReviewedYamlChanged("   \n  "))

        viewModel.onAction(CreateServiceUiAction.ApplySubmitted)
        advanceUntilIdle()

        assertTrue(resources.createCalls.isEmpty())
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `apply failure keeps review step edits and shows friendly error`() = vmTest { viewModel ->
        resources.createResult =
            Result.Error(AppError.Unknown(message = "forbidden: services is forbidden"))
        fillValidForm(viewModel)
        viewModel.onAction(CreateServiceUiAction.PreviewSubmitted)
        val edited = viewModel.uiState.value.reviewedYaml + "\n# keep this edit"
        viewModel.onAction(CreateServiceUiAction.ReviewedYamlChanged(edited))

        viewModel.onAction(CreateServiceUiAction.ApplySubmitted)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Still on review with every edit preserved for another attempt.
        assertEquals(CreateServiceStep.REVIEW, state.step)
        assertEquals(edited, state.reviewedYaml)
        assertEquals("web-app", state.name)
        assertEquals("web-app", state.selectorApp)
        assertEquals("80", state.port)
        assertEquals("8080", state.targetPort)
        assertEquals("team-a", state.namespace)
        assertFalse(state.isSubmitting)
        assertEquals(
            "forbidden: services is forbidden",
            state.errorMessage,
        )
    }

    @Test
    fun `new namespace name validates live`() = vmTest { viewModel ->
        viewModel.onAction(CreateServiceUiAction.NewNamespaceNameChanged("Team_A"))
        assertEquals(
            "Use lowercase letters, numbers, and hyphens. It must start and end with a letter or number.",
            viewModel.uiState.value.newNamespaceError,
        )

        viewModel.onAction(CreateServiceUiAction.NewNamespaceNameChanged("-leading"))
        assertNotNull(viewModel.uiState.value.newNamespaceError)

        viewModel.onAction(CreateServiceUiAction.NewNamespaceNameChanged("a".repeat(64)))
        assertEquals("Must be 63 characters or fewer", viewModel.uiState.value.newNamespaceError)

        viewModel.onAction(CreateServiceUiAction.NewNamespaceNameChanged("team-b"))
        assertNull(viewModel.uiState.value.newNamespaceError)
    }

    @Test
    fun `namespace creation success appends selects and emits effect`() = vmTest { viewModel ->
        viewModel.onAction(CreateServiceUiAction.CreateNamespaceClicked)
        assertTrue(viewModel.uiState.value.showCreateNamespaceDialog)
        viewModel.onAction(CreateServiceUiAction.NewNamespaceNameChanged("team-b"))

        val effects = mutableListOf<CreateServiceUiEffect>()
        val collector = launch { viewModel.effects.collect { effects += it } }
        runCurrent()

        viewModel.onAction(CreateServiceUiAction.CreateNamespaceSubmitted)
        advanceUntilIdle()
        collector.cancel()

        val state = viewModel.uiState.value
        assertFalse(state.showCreateNamespaceDialog)
        assertFalse(state.isCreatingNamespace)
        assertEquals(listOf("default", "team-a", "team-b"), state.availableNamespaces)
        assertEquals("team-b", state.namespace)
        assertEquals(listOf(CreateServiceUiEffect.NamespaceCreated), effects)
        assertEquals(listOf("c-1" to "team-b"), namespaces.created)
    }

    @Test
    fun `namespace creation failure keeps dialog input and shows friendly message`() =
        vmTest { viewModel ->
            namespaces.createResult = Result.Error(AppError.Unknown(message = "already exists"))
            viewModel.onAction(CreateServiceUiAction.CreateNamespaceClicked)
            viewModel.onAction(CreateServiceUiAction.NewNamespaceNameChanged("team-b"))

            viewModel.onAction(CreateServiceUiAction.CreateNamespaceSubmitted)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.showCreateNamespaceDialog)
            assertEquals("team-b", state.newNamespaceName)
            assertFalse(state.isCreatingNamespace)
            assertEquals(
                "already exists",
                state.newNamespaceError,
            )
            assertFalse(state.availableNamespaces.contains("team-b"))
        }
}
