package io.github.dockhand.ui.screen.home

import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.domain.model.FileListPage
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.domain.model.FilePlaceGroup
import io.github.dockhand.domain.model.FilePlaceKind
import io.github.dockhand.domain.model.FileSource
import io.github.dockhand.domain.model.FileSourceKind
import io.github.dockhand.domain.model.breadcrumbs
import io.github.dockhand.domain.repository.FileBrowserRepository
import io.github.dockhand.ui.component.FileContextAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeViewModelTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun startsWithHomeOnLeftAndRootOnRight() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )

        assertEquals(PaneId.Left, viewModel.state.value.focusedPane)
        assertEquals(fixture.desktop.path, viewModel.state.value.leftPane.location?.path)
        assertEquals(fixture.desktop.path, viewModel.state.value.rightPane.location?.path)
        assertEquals(listOf("Documents", "readme.txt"), viewModel.state.value.leftPane.entries.map { it.name })
    }

    @Test
    fun doubleClickingDirectoryNavigatesFocusedPaneAndUpdatesTopBarPath() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )
        val documents = viewModel.state.value.leftPane.entries.first { it.name == "Documents" }

        viewModel.onEntryDoubleClick(PaneId.Left, documents.entry)

        assertEquals(PaneId.Left, viewModel.state.value.focusedPane)
        assertEquals(fixture.documents.path, viewModel.state.value.focusedLocation?.path)
        assertEquals("Documents", viewModel.state.value.focusedLocation?.breadcrumbs()?.last()?.label)
    }

    @Test
    fun doubleClickingInactivePaneSwitchesFocusAndNavigatesIt() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )
        val documents = viewModel.state.value.rightPane.entries.first { it.name == "Documents" }

        viewModel.onEntryDoubleClick(PaneId.Right, documents.entry)

        assertEquals(PaneId.Right, viewModel.state.value.focusedPane)
        assertEquals(fixture.documents.path, viewModel.state.value.focusedLocation?.path)
        // 左面板保持原路径
        assertEquals(fixture.desktop.path, viewModel.state.value.leftPane.location?.path)
    }

    @Test
    fun singleClickingFileSelectsWithoutNavigating() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )
        val file = viewModel.state.value.leftPane.entries.first { it.name == "readme.txt" }

        viewModel.onEntryClick(PaneId.Left, file.entry)

        assertEquals(fixture.desktop.path, viewModel.state.value.leftPane.location?.path)
        assertEquals(setOf(file.id), viewModel.state.value.leftPane.selectedIds)
    }

    @Test
    fun singleClickingDirectoryOnlySelectsAndFocusesPane() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )
        val documents = viewModel.state.value.leftPane.entries.first { it.name == "Documents" }

        viewModel.onEntryClick(PaneId.Left, documents.entry)

        assertEquals(PaneId.Left, viewModel.state.value.focusedPane)
        assertEquals(fixture.desktop.path, viewModel.state.value.leftPane.location?.path)
        assertEquals(setOf(documents.id), viewModel.state.value.leftPane.selectedIds)
    }

    @Test
    fun secondaryClickingEntryOnlySelectsAndFocusesPane() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )
        val documents = viewModel.state.value.rightPane.entries.first { it.name == "Documents" }

        viewModel.onEntrySecondaryClick(PaneId.Right, documents.entry)

        assertEquals(PaneId.Right, viewModel.state.value.focusedPane)
        assertEquals(fixture.desktop.path, viewModel.state.value.leftPane.location?.path)
        assertEquals(fixture.desktop.path, viewModel.state.value.rightPane.location?.path)
        assertEquals(setOf(documents.id), viewModel.state.value.rightPane.selectedIds)
    }

    @Test
    fun drawerPlacesCanBeReorderedWithinCategory() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )
        val documents = fixture.places.first { it.kind == FilePlaceKind.Documents }
        val home = fixture.places.first { it.kind == FilePlaceKind.Home }
        val desktop = fixture.places.first { it.kind == FilePlaceKind.Desktop }

        viewModel.onMovePlace(documents, -1)
        assertEquals(
            listOf(home.id, documents.id, desktop.id),
            viewModel.state.value.drawerPlaceOrder[FilePlaceGroup.Local],
        )

        viewModel.onMovePlace(documents, -1)
        assertEquals(
            listOf(documents.id, home.id, desktop.id),
            viewModel.state.value.drawerPlaceOrder[FilePlaceGroup.Local],
        )
    }

    @Test
    fun mtpStorageClickSelectsRootThenTogglesWhenSelected() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )
        val source = FileSource("mtp:1:65537", "MTP 存储", FileSourceKind.Mtp)
        val storage = FilePlace(
            id = source.id,
            label = source.name,
            location = FileLocation(source, "/"),
            kind = FilePlaceKind.MtpStorage,
            group = FilePlaceGroup.Mtp,
        )

        // 未选中：只跳转根目录，不展开。
        viewModel.onMtpStorageClick(storage, selected = false)

        assertEquals(source.id, viewModel.state.value.leftPane.location?.source?.id)
        assertEquals("/", viewModel.state.value.leftPane.location?.path)
        assertEquals(emptySet<String>(), viewModel.state.value.mtpExpandedSources)

        // 已选中：只切换展开状态。
        viewModel.onMtpStorageClick(storage, selected = true)

        assertTrue(source.id in viewModel.state.value.mtpExpandedSources)
        assertEquals(source.id, viewModel.state.value.leftPane.location?.source?.id)
        assertEquals("/", viewModel.state.value.leftPane.location?.path)
    }

    @Test
    fun mtpFirstPageAndLoadMoreAppendEntries() = runBlocking {
        val source = FileSource("mtp:1:65537", "MTP", FileSourceKind.Mtp)
        val root = FileLocation(source, "/")
        val allEntries = (1..650).map { index ->
            FileEntry(
                id = root.path + "file-$index",
                name = "file-$index.txt",
                location = FileLocation(source, "/file-$index.txt"),
                isDirectory = false,
                size = index.toLong(),
            )
        }
        val repository = object : FileBrowserRepository {
            override fun observePlaces(): Flow<List<FilePlace>> = flowOf(
                listOf(
                    FilePlace(
                        id = source.id,
                        label = source.name,
                        location = root,
                        kind = FilePlaceKind.MtpStorage,
                        group = FilePlaceGroup.Mtp,
                    )
                )
            )

            override fun observeChanges(location: FileLocation): Flow<Unit> = flowOf()

            override suspend fun list(location: FileLocation): List<FileEntry> = allEntries

            override suspend fun listPage(
                location: FileLocation,
                offset: Int,
                limit: Int,
            ): FileListPage {
                val page = allEntries.drop(offset).take(limit)
                return FileListPage(
                    entries = page,
                    hasMore = offset + page.size < allEntries.size,
                    total = allEntries.size,
                )
            }

            override suspend fun createDirectory(parent: FileLocation, name: String) = Unit

            override suspend fun createFile(parent: FileLocation, name: String) = Unit

            override suspend fun copy(
                entries: List<FileEntry>,
                destinationDirectory: FileLocation,
                overwrite: Boolean,
            ) = Unit
        }
        val viewModel = HomeViewModel(
            files = repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )

        assertEquals(300, viewModel.state.value.leftPane.entries.size)
        assertTrue(viewModel.state.value.leftPane.hasMore)

        viewModel.onLoadMore(PaneId.Left)

        assertEquals(600, viewModel.state.value.leftPane.entries.size)
        assertTrue(viewModel.state.value.leftPane.hasMore)
    }

    @Test
    fun eachViewInfoActionOpensAnIndependentWindow() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )

        viewModel.onEntryContextAction(
            paneId = PaneId.Left,
            entry = fixture.readme,
            action = FileContextAction.ViewInfo,
        )
        viewModel.onEntryContextAction(
            paneId = PaneId.Left,
            entry = fixture.documentsEntry,
            action = FileContextAction.ViewInfo,
        )

        val dialogs = viewModel.state.value.fileInfoDialogs
        assertEquals(2, dialogs.size)
        assertTrue(dialogs[0].id != dialogs[1].id)

        viewModel.onDismissFileInfo(dialogs[0].id)
        assertEquals(1, viewModel.state.value.fileInfoDialogs.size)
        assertEquals(dialogs[1].id, viewModel.state.value.fileInfoDialogs.single().id)
    }

    @Test
    fun mtpStorageExpansionCanBeToggled() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )
        val sourceId = "mtp:1:65537"

        viewModel.onToggleMtpExpanded(sourceId)
        assertEquals(setOf(sourceId), viewModel.state.value.mtpExpandedSources)

        viewModel.onToggleMtpExpanded(sourceId)
        assertEquals(emptySet<String>(), viewModel.state.value.mtpExpandedSources)
    }

    @Test
    fun mtpShortcutsCanBeReordered() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )

        assertEquals(
            listOf("camera", "downloads", "movies", "music", "pictures"),
            viewModel.state.value.mtpShortcutOrder,
        )

        viewModel.onMoveMtpShortcut("pictures", -1)

        assertEquals(
            listOf("camera", "downloads", "movies", "pictures", "music"),
            viewModel.state.value.mtpShortcutOrder,
        )
    }

    @Test
    fun sidebarAndBreadcrumbNavigateTheFocusedPane() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )

        viewModel.onPlaceClick(fixture.documentsPlace)
        assertEquals(fixture.documents.path, viewModel.state.value.focusedLocation?.path)

        val homeCrumb = viewModel.state.value.focusedLocation!!
            .breadcrumbs()
            .first { it.label == "lumkit" }
        viewModel.onBreadcrumbClick(homeCrumb)
        assertEquals(fixture.home.path, viewModel.state.value.focusedLocation?.path)

        viewModel.onGoUp(PaneId.Left)
        assertEquals("/Users", viewModel.state.value.focusedLocation?.path)
    }

    @Test
    fun backAndForwardRestoreHistory() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )

        viewModel.onEntryDoubleClick(
            PaneId.Left,
            viewModel.state.value.leftPane.entries.first { it.name == "Documents" }.entry
        )
        viewModel.onBack(PaneId.Left)
        assertEquals(fixture.desktop.path, viewModel.state.value.leftPane.location?.path)

        viewModel.onForward(PaneId.Left)
        assertEquals(fixture.documents.path, viewModel.state.value.leftPane.location?.path)
    }

    @Test
    fun createsFolderInFocusedPane() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )

        viewModel.onCreate(PaneId.Left, "NewFolder", createFile = false)

        assertEquals(listOf(fixture.desktop to "NewFolder"), fixture.createdDirectories)
        assertEquals(null, viewModel.state.value.operationError)
    }

    @Test
    fun createsFileWhenCheckboxIsChecked() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )

        viewModel.onCreate(PaneId.Left, "notes.txt", createFile = true)

        assertEquals(listOf(fixture.desktop to "notes.txt"), fixture.createdFiles)
        assertEquals(emptyList(), fixture.createdDirectories)
    }

    @Test
    fun syncNavigatesOtherPaneToFocusedDirectory() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )

        viewModel.onSyncOtherPane(PaneId.Left)

        assertEquals(PaneId.Left, viewModel.state.value.focusedPane)
        assertEquals(fixture.desktop.path, viewModel.state.value.leftPane.location?.path)
        assertEquals(fixture.desktop.path, viewModel.state.value.rightPane.location?.path)
    }

    @Test
    fun directoryChangesRefreshBothPanesAutomatically() = runBlocking {
        val fixture = Fixture()
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )
        val desktopCallsBefore = fixture.listCalls[fixture.desktop.path] ?: 0

        fixture.changes.emit(Unit)

        assertTrue((fixture.listCalls[fixture.desktop.path] ?: 0) > desktopCallsBefore)
    }

    @Test
    fun missingLocationProducesErrorMessage() = runBlocking {
        val fixture = Fixture()
        fixture.failOnPath = fixture.documents.path
        val viewModel = HomeViewModel(
            files = fixture.repository,
            computeDispatcher = Dispatchers.Unconfined,
            externalScope = scope,
        )

        viewModel.onEntryDoubleClick(
            PaneId.Left,
            viewModel.state.value.leftPane.entries.first { it.name == "Documents" }.entry
        )

        assertEquals("无法读取 Documents", viewModel.state.value.leftPane.errorMessage)
        assertEquals(fixture.documents.path, viewModel.state.value.leftPane.location?.path)
    }

    class Fixture {
        val source = FileSource(FileSource.LOCAL_ID, "本机", FileSourceKind.Local)
        val root = FileLocation(source, "/")
        val users = FileLocation(source, "/Users")
        val home = FileLocation(source, "/Users/lumkit")
        val desktop = FileLocation(source, "/Users/lumkit/Desktop")
        val documents = FileLocation(source, "/Users/lumkit/Documents")
        var failOnPath: String? = null
        val createdDirectories = mutableListOf<Pair<FileLocation, String>>()
        val createdFiles = mutableListOf<Pair<FileLocation, String>>()
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val listCalls = mutableMapOf<String, Int>()

        val documentsEntry = dir("Documents", documents)
        val readme = file("readme.txt", home)
        val usersEntry = dir("Users", users)

        val places = listOf(
            FilePlace("local:home", "lumkit", home, FilePlaceKind.Home, FilePlaceGroup.Local),
            FilePlace("local:desktop", "Desktop", desktop, FilePlaceKind.Desktop, FilePlaceGroup.Local),
            FilePlace("local:documents", "Documents", documents, FilePlaceKind.Documents, FilePlaceGroup.Local),
        )
        val documentsPlace = places.first { it.kind == FilePlaceKind.Documents }

        private val contents = mapOf(
            home.path to listOf(documentsEntry, readme),
            desktop.path to listOf(documentsEntry, readme),
            root.path to listOf(usersEntry),
            users.path to emptyList(),
            documents.path to emptyList(),
        )

        val repository = object : FileBrowserRepository {
            override fun observePlaces(): Flow<List<FilePlace>> = flowOf(places)

            override fun observeChanges(location: FileLocation): Flow<Unit> = changes

            override suspend fun list(location: FileLocation): List<FileEntry> {
                listCalls[location.path] = (listCalls[location.path] ?: 0) + 1
                if (location.path == failOnPath) throw IllegalStateException("无法读取 Documents")
                return contents[location.path].orEmpty()
            }

            override suspend fun createDirectory(parent: FileLocation, name: String) {
                createdDirectories += parent to name
            }

            override suspend fun createFile(parent: FileLocation, name: String) {
                createdFiles += parent to name
            }

            override suspend fun copy(
                entries: List<FileEntry>,
                destinationDirectory: FileLocation,
                overwrite: Boolean,
            ) = Unit
        }

        private fun dir(name: String, location: FileLocation) = FileEntry(
            id = location.path,
            name = name,
            location = location,
            isDirectory = true,
        )

        private fun file(name: String, parent: FileLocation) = FileEntry(
            id = parent.path + "/" + name,
            name = name,
            location = FileLocation(source, parent.path + "/" + name),
            isDirectory = false,
            size = 1,
        )
    }
}