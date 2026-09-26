package dev.hridaya.kubenexus.presentation.pods.create

import androidx.lifecycle.viewModelScope
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.CommandExecResult
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.TerminalSession
import dev.hridaya.kubenexus.domain.repository.PodRepository
import dev.hridaya.kubenexus.domain.usecase.CreateNamespaceUseCase
import dev.hridaya.kubenexus.domain.usecase.CreatePodUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
class CreatePodViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakePodRepository: FakePodRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePodRepository = FakePodRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vmTest(block: suspend TestScope.(CreatePodViewModel) -> Unit) =
        runTest(testDispatcher) {
            val viewModel = CreatePodViewModel(
                clusterId = "c-1",
                initialNamespace = "team-a",
                initialAvailableNamespaces = listOf("default", "team-a"),
                createPodUseCase = CreatePodUseCase(fakePodRepository),
                createNamespaceUseCase = CreateNamespaceUseCase(fakePodRepository),
                dispatcherProvider = testDispatcherProvider,
            )
            try {
                block(viewModel)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    private fun fillValidForm(viewModel: CreatePodViewModel) {
        viewModel.onAction(CreatePodUiAction.NameChanged("web-app"))
        viewModel.onAction(CreatePodUiAction.ImageChanged("nginx:1.27"))
        viewModel.onAction(CreatePodUiAction.ContainerPortChanged("8080"))
        // namespace keeps its "team-a" initial value.
    }

    @Test
    fun `initial form state does not show required field errors before submit`() =
        vmTest { viewModel ->
            val state = viewModel.uiState.value
            assertTrue(state.fieldErrors.isEmpty())
            assertNull(state.errorMessage)

            viewModel.onAction(CreatePodUiAction.PreviewSubmitted)
            val submittedState = viewModel.uiState.value
            assertTrue(submittedState.fieldErrors.containsKey("name"))
            assertTrue(submittedState.fieldErrors.containsKey("image"))
            assertNotNull(submittedState.errorMessage)
        }

    @Test
    fun `preview success moves to review step with generated yaml`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        assertTrue(viewModel.uiState.value.fieldErrors.isEmpty())

        viewModel.onAction(CreatePodUiAction.PreviewSubmitted)

        val state = viewModel.uiState.value
        assertEquals(CreatePodStep.REVIEW, state.step)
        assertNull(state.errorMessage)
        assertNotNull(state.generatedYaml)
        val yaml = state.generatedYaml!!
        assertTrue(yaml.contains("kind: Pod"))
        assertTrue(yaml.contains("name: web-app"))
        assertTrue(yaml.contains("namespace: team-a"))
        assertTrue(yaml.contains("image: nginx:1.27"))
        assertTrue(yaml.contains("containerPort: 8080"))
    }

    @Test
    fun `invalid draft surfaces field errors without clearing input`() = vmTest { viewModel ->
        viewModel.onAction(CreatePodUiAction.NameChanged("Invalid_Name"))
        viewModel.onAction(CreatePodUiAction.ImageChanged("nginx 1.27"))
        viewModel.onAction(CreatePodUiAction.ContainerPortChanged("abc"))

        val state = viewModel.uiState.value
        assertEquals(
            setOf("name", "image", "containerPort"),
            state.fieldErrors.keys,
        )
        // Every keystroke survived validation - input is never discarded.
        assertEquals("Invalid_Name", state.name)
        assertEquals("nginx 1.27", state.image)
        assertEquals("abc", state.containerPort)
        assertEquals("team-a", state.namespace)

        // Preview is blocked while the draft is invalid.
        viewModel.onAction(CreatePodUiAction.PreviewSubmitted)
        assertEquals(CreatePodStep.FORM, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.generatedYaml)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Errors clear live as the user fixes the input.
        viewModel.onAction(CreatePodUiAction.NameChanged("web-app"))
        assertFalse(viewModel.uiState.value.fieldErrors.containsKey("name"))
        viewModel.onAction(CreatePodUiAction.ContainerPortChanged("8080"))
        assertFalse(viewModel.uiState.value.fieldErrors.containsKey("containerPort"))
    }

    @Test
    fun `apply success emits created effect and applies the reviewed yaml`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreatePodUiAction.PreviewSubmitted)
        val reviewedYaml = viewModel.uiState.value.reviewedYaml
        assertNotNull(reviewedYaml)

        val effects = mutableListOf<CreatePodUiEffect>()
        val collector = launch { viewModel.effects.collect { effects += it } }
        runCurrent()

        viewModel.onAction(CreatePodUiAction.ApplySubmitted)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(listOf(CreatePodUiEffect.Created("web-app")), effects)
        assertEquals(1, fakePodRepository.appliedManifests.size)
        val (clusterId, manifest) = fakePodRepository.appliedManifests.single()
        assertEquals("c-1", clusterId)
        assertEquals(reviewedYaml, manifest)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(CreatePodStep.FORM, viewModel.uiState.value.step)
        assertEquals("", viewModel.uiState.value.name)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `reset action clears form inputs to initial state`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreatePodUiAction.Reset)
        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals("", state.image)
        assertEquals(CreatePodStep.FORM, state.step)
    }

    @Test
    fun `edited yaml is applied verbatim without regeneration`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreatePodUiAction.PreviewSubmitted)
        val original = viewModel.uiState.value.reviewedYaml
        val edited = "$original# tuned by hand"
        viewModel.onAction(CreatePodUiAction.ReviewedYamlChanged(edited))

        viewModel.onAction(CreatePodUiAction.ApplySubmitted)
        advanceUntilIdle()

        val (_, manifest) = fakePodRepository.appliedManifests.single()
        assertEquals(edited, manifest)
        assertFalse(manifest == original)
    }

    @Test
    fun `apply is blocked when the editor content is blank`() = vmTest { viewModel ->
        fillValidForm(viewModel)
        viewModel.onAction(CreatePodUiAction.PreviewSubmitted)
        viewModel.onAction(CreatePodUiAction.ReviewedYamlChanged("   \n  "))

        viewModel.onAction(CreatePodUiAction.ApplySubmitted)
        advanceUntilIdle()

        assertTrue(fakePodRepository.appliedManifests.isEmpty())
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `apply failure keeps review step edits and shows friendly error`() = vmTest { viewModel ->
        fakePodRepository.applyError =
            AppError.Unknown(message = "forbidden: pods is forbidden")
        fillValidForm(viewModel)
        viewModel.onAction(CreatePodUiAction.PreviewSubmitted)
        val edited = viewModel.uiState.value.reviewedYaml + "\n# keep this edit"
        viewModel.onAction(CreatePodUiAction.ReviewedYamlChanged(edited))

        viewModel.onAction(CreatePodUiAction.ApplySubmitted)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Still on review with every edit preserved for another attempt.
        assertEquals(CreatePodStep.REVIEW, state.step)
        assertEquals(edited, state.reviewedYaml)
        assertEquals("web-app", state.name)
        assertEquals("nginx:1.27", state.image)
        assertEquals("8080", state.containerPort)
        assertEquals("team-a", state.namespace)
        assertFalse(state.isSubmitting)
        assertEquals(
            "Couldn't create the pod. Please try again in a moment.",
            state.errorMessage,
        )
    }

    @Test
    fun `new namespace name validates live`() = vmTest { viewModel ->
        viewModel.onAction(CreatePodUiAction.NewNamespaceNameChanged("Team_A"))
        assertEquals(
            "Use lowercase letters, numbers, and hyphens. It must start and end with a letter or number.",
            viewModel.uiState.value.newNamespaceError,
        )

        viewModel.onAction(CreatePodUiAction.NewNamespaceNameChanged("-leading"))
        assertNotNull(viewModel.uiState.value.newNamespaceError)

        viewModel.onAction(CreatePodUiAction.NewNamespaceNameChanged("a".repeat(64)))
        assertEquals("Must be 63 characters or fewer", viewModel.uiState.value.newNamespaceError)

        viewModel.onAction(CreatePodUiAction.NewNamespaceNameChanged("team-b"))
        assertNull(viewModel.uiState.value.newNamespaceError)
    }

    @Test
    fun `namespace creation success appends selects and emits effect`() = vmTest { viewModel ->
        viewModel.onAction(CreatePodUiAction.CreateNamespaceClicked)
        assertTrue(viewModel.uiState.value.showCreateNamespaceDialog)
        viewModel.onAction(CreatePodUiAction.NewNamespaceNameChanged("team-b"))

        val effects = mutableListOf<CreatePodUiEffect>()
        val collector = launch { viewModel.effects.collect { effects += it } }
        runCurrent()

        viewModel.onAction(CreatePodUiAction.CreateNamespaceSubmitted)
        advanceUntilIdle()
        collector.cancel()

        val state = viewModel.uiState.value
        assertFalse(state.showCreateNamespaceDialog)
        assertFalse(state.isCreatingNamespace)
        assertEquals(listOf("default", "team-a", "team-b"), state.availableNamespaces)
        assertEquals("team-b", state.namespace)
        assertEquals(listOf(CreatePodUiEffect.NamespaceCreated), effects)
        assertEquals(listOf("c-1" to "team-b"), fakePodRepository.createdNamespaces)
    }

    @Test
    fun `namespace creation failure keeps dialog input and shows friendly message`() =
        vmTest { viewModel ->
            fakePodRepository.createError = AppError.Unknown(message = "already exists")
            viewModel.onAction(CreatePodUiAction.CreateNamespaceClicked)
            viewModel.onAction(CreatePodUiAction.NewNamespaceNameChanged("team-b"))

            viewModel.onAction(CreatePodUiAction.CreateNamespaceSubmitted)
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

/** Only [createNamespace] and [createPodFromManifest] are exercised here; everything else is inert. */
private class FakePodRepository : PodRepository {
    var applyError: AppError? = null
    val appliedManifests = mutableListOf<Pair<String?, String>>()
    var createError: AppError? = null
    val createdNamespaces = mutableListOf<Pair<String?, String>>()

    override suspend fun createNamespace(clusterId: String?, name: String): Result<Unit> {
        createdNamespaces += clusterId to name
        val error = createError ?: return Result.Success(Unit)
        return Result.Error(error)
    }

    override suspend fun createPodFromManifest(
        clusterId: String?,
        manifestYaml: String,
    ): Result<Unit> {
        appliedManifests += clusterId to manifestYaml
        val error = applyError ?: return Result.Success(Unit)
        return Result.Error(error)
    }

    override suspend fun listPodsBySelector(
        rawKubeconfig: String,
        namespace: String?,
        labelSelector: String,
    ): Result<List<Pod>> = Result.Success(emptyList())

    override suspend fun getPodsBySelector(
        clusterId: String?,
        namespace: String?,
        labelSelector: String,
    ): Result<List<Pod>> = Result.Success(emptyList())

    override fun getPodsStream(clusterId: String?, namespace: String?): Flow<List<Pod>> =
        flowOf(emptyList())

    override fun getNamespacesStream(clusterId: String?): Flow<List<String>> =
        flowOf(emptyList())

    override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> = flowOf(null)

    override suspend fun refreshWorkloads(clusterId: String?, namespace: String?): Result<Unit> =
        Result.Success(Unit)

    override suspend fun describePod(
        clusterId: String?,
        namespace: String,
        podName: String,
    ): Result<PodDetails> = Result.Error(AppError.Unknown(message = "not used"))

    override suspend fun getPodMetrics(
        clusterId: String?,
        namespace: String?,
    ): Result<List<PodMetricSample>> = Result.Success(emptyList())

    override suspend fun getSinglePodMetrics(
        clusterId: String?,
        namespace: String,
        podName: String,
    ): Result<PodMetricSample?> = Result.Success(null)

    override suspend fun deletePod(
        clusterId: String?,
        namespace: String,
        podName: String,
    ): Result<Unit> = Result.Success(Unit)

    override suspend fun deleteNamespace(clusterId: String?, namespace: String): Result<Unit> =
        Result.Success(Unit)

    override suspend fun getPodLogs(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String?,
        tailLines: Long?,
    ): Result<String> = Result.Success("")

    override fun streamPodLogs(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String?,
        tailLines: Long?,
    ): Flow<String> = emptyFlow()

    override suspend fun execCommand(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String,
        command: String,
        stdin: String,
    ): Result<CommandExecResult> = Result.Error(AppError.Unknown(message = "not used"))

    override suspend fun startTerminalSession(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ): Result<TerminalSession> = Result.Error(AppError.Unknown(message = "not used"))

    override suspend fun startExecSession(
        clusterId: String?,
        namespace: String,
        podName: String,
        containerName: String,
        command: String,
        tty: Boolean,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ): Result<TerminalSession> = Result.Error(AppError.Unknown(message = "not used"))
}
