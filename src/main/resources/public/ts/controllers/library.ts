import { ng, template, idiom, notify } from 'entcore';
import { Folders, Folder, Blog, Filters, BaseFolder, Root } from '../models';
import { _ } from 'entcore';
import { BlogModel } from './commons';

export interface LibraryControllerScope {
    root: Root
    blog: BlogModel
    folder: Folder
    currentFolder: Folder | Root
    filters: typeof Filters
    displayLib: {
        data: any
        showShare: boolean
        targetFolder: Folder
        searchBlogs: string
        lightbox: {
            manageBlogs: boolean,
            properties: boolean
        }
    }
    shareBlog(): void
    restore(): void;
    move(): void;
    open(blog: Blog): void;
    openRoot(): void;
    openTrash(): void;
    editBlogProperties(): void;
    lightbox(name: string, data?: any): void;
    closeLightbox(name: string, data?: any): void;
    saveProperties(): void;
    can(right: string): boolean
    searchBlog(blog: Blog): void;
    searchFolder(folder: Folder): void;
    createFolder(): void;
    trashSelection(): void;
    removeSelection(): void;
    duplicateBlogs(): void;
    createBlogView(): void;
    viewBlog(blog: Blog, ev?: Event): void;
    openFolder(folder: Folder): void;
    selectionContains(folder: Folder): boolean;
    dropTo(targetItem: string | Folder, $originalEvent): void;
    removeBlog(): void;
    //
    $apply: any
    display: any
}
export function LibraryDelegate($scope: LibraryControllerScope, $rootScope, $location) {
    $scope.displayLib = {
        data: undefined,
        showShare: false,
        lightbox: {
            manageBlogs: false,
            properties: false
        },
        searchBlogs: "",
        targetFolder: undefined
    }
    template.open('library/folder-content', 'library/folder-content');
    $scope.currentFolder = Folders.root;
    $scope.currentFolder.sync();
    $scope.root = Folders.root;
    $scope.folder = new Folder();
    $scope.filters = Filters;
    $scope.filters.mine = true;
    $scope.display = {
        publishType: undefined
    };

    template.open('library/create-blog', 'library/create-blog');
    template.open('library/toaster', 'library/toaster');
    template.open('library/publish', 'library/publish');
    template.open('library/properties', 'library/properties');
    template.open('library/move', 'library/move');

    BaseFolder.eventer.on('refresh', () => $scope.$apply());
    Blog.eventer.on('save', () => $scope.$apply());

    $rootScope.$on('share-updated', async (event, changes) => {
        for (let blog of $scope.currentFolder.selection) {
            await (blog as Blog).sync();
        }

        $scope.$apply();
    });
    //=== Private methods
    const resetSelection=()=>{
        $scope.currentFolder.deselectAll();
    }
    //=== Public methods
    $scope.searchBlog = (item: Blog) => {
        return !$scope.displayLib.searchBlogs || idiom.removeAccents(item.title.toLowerCase()).indexOf(
            idiom.removeAccents($scope.displayLib.searchBlogs).toLowerCase()
        ) !== -1;
    };

    $scope.searchFolder = (item: Folder) => {
        return !$scope.displayLib.searchBlogs || idiom.removeAccents(item.name.toLowerCase()).indexOf(
            idiom.removeAccents($scope.displayLib.searchBlogs).toLowerCase()
        ) !== -1;
    };

    $scope.can = (right: string) => {
        let folder: Folder | Root = $scope.currentFolder;
        return folder.ressources.sel.selected.find((w: Blog) => !w.myRights[right]) === undefined;
    };

    $scope.saveProperties = async () => {
        $scope.lightbox('properties');
        //adapt old model to new
        const blog = Folders.root.findRessource($scope.blog._id) || new Blog();
        blog.fromJSON($scope.blog.toJSON() as any);
        await blog.save();
        $location.path("/list-blogs");
        $scope.$apply();
    }

    $scope.removeBlog = async function () {
        const blog = Folders.root.findRessource($scope.blog._id);
        if (blog) {
            await blog.remove();
        }
        $location.path('/list-blogs');
    }

    $scope.editBlogProperties = () => {
        const blog = $scope.currentFolder.selection[0] as Blog;
        $location.path('/edit/' + blog._id);
    };

    $scope.openFolder = (folder) => {
        resetSelection();
        template.open('library/folder-content', 'library/folder-content');
        $scope.currentFolder = folder;
        $scope.currentFolder.sync();
    };

    $scope.createFolder = async () => {
        $scope.folder.parentId = $scope.currentFolder._id;
        $scope.displayLib.lightbox['newFolder'] = false;
        $scope.currentFolder.children.push($scope.folder);
        await $scope.folder.save();
        $scope.folder = new Folder();
    };

    $scope.trashSelection = async () => {
        $scope.closeLightbox('confirmRemove');
        await $scope.currentFolder.trashSelection();
        $scope.$apply();
        notify.info('blog.selection.trashed');
    }

    $scope.removeSelection = async () => {
        $scope.closeLightbox('confirmRemove');
        await $scope.currentFolder.removeSelection();
        $scope.$apply();
        notify.info('blog.selection.removed');
    }

    $scope.openTrash = () => {
        resetSelection();
        template.open('library/folder-content', 'library/trash');
        $scope.currentFolder = Folders.trash;
        Folders.trash.sync();
    };

    $scope.openRoot = () => {
        resetSelection();
        template.open('library/folder-content', 'library/folder-content');
        $scope.currentFolder = Folders.root;
        Folders.root.sync();
    };

    $scope.viewBlog = (blog: Blog, ev) => {
        resetSelection();
        ev && ev.stopPropagation();
        $location.path('/view/' + blog._id);
    };

    $scope.open = (item: Blog | Folder) => {
        resetSelection();
        if (item instanceof Blog) {
            $scope.viewBlog(item);
        }
        else {
            $scope.openFolder(item);
        }
    };

    $scope.dropTo = async (targetItem: string | Folder, $originalEvent) => {
        let dataField = $originalEvent.dataTransfer.types.indexOf && $originalEvent.dataTransfer.types.indexOf("application/json") > -1 ? "application/json" : //Chrome & Safari
            $originalEvent.dataTransfer.types.contains && $originalEvent.dataTransfer.types.contains("Text") ? "Text" : //IE
                undefined;
        let originalItem: string = JSON.parse($originalEvent.dataTransfer.getData(dataField));

        if (targetItem instanceof Folder && originalItem === targetItem._id) {
            return;
        }
        let blogs = await Folders.ressources();
        let actualItem: Blog | Folder = blogs.find(w => w._id === originalItem);
        if (!actualItem) {
            let folders = await Folders.folders();
            actualItem = folders.find(f => f._id === originalItem);
        }
        await actualItem.moveTo(targetItem);
        await $scope.currentFolder.sync();
        $scope.$apply();
        if (targetItem instanceof Folder) {
            targetItem.save();
        }
    };

    $scope.selectionContains = (folder: Folder) => {
        let contains = false;
        let selection: (Blog | Folder)[] = $scope.currentFolder.selection;
        selection.forEach((item) => {
            if (item instanceof Folder) {
                contains = contains || item.contains(folder) || item._id === folder._id;
            }
        });

        return contains;
    }

    $scope.move = async () => {
        $scope.lightbox('move')
        let folder = $scope.currentFolder as Folder;
        await folder.moveSelectionTo($scope.displayLib.targetFolder);
        await Folders.root.sync();
        await $scope.currentFolder.sync();
        await $scope.displayLib.targetFolder.sync();
        $scope.$apply();
    };

    $scope.duplicateBlogs = async () => {
        let folder = $scope.currentFolder as Folder;
        await folder.ressources.duplicateSelection();
        $scope.$apply();
    };

    $scope.restore = async () => {
        await Folders.trash.restoreSelection();
        $scope.$apply();
    };

    $scope.lightbox = function (lightboxName: string, data: any) {
        $scope.displayLib.data = data;
        $scope.displayLib.lightbox[lightboxName] = !$scope.displayLib.lightbox[lightboxName];
    };

    $scope.closeLightbox = function (lightboxName: string, data: any) {
        $scope.displayLib.data = data;
        $scope.displayLib.lightbox[lightboxName] = false;
    };

    $scope.shareBlog = function () {
        $scope.displayLib.showShare = true;
        let same = true;
        const selected = $scope.currentFolder.selection;
        let publishType = selected[0]['publish-type'];
        selected.forEach((blog) => {
            same = same && (blog['publish-type'] === publishType);
        });
        if (same) {
            $scope.display.publishType = publishType;
        }
        else {
            $scope.display.publishType = undefined;
        }
    }
}