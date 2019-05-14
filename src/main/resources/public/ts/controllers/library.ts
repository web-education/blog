import { ng, template, idiom, notify } from 'entcore';
import { Folders, Folder, Blog, Filters, BaseFolder, Root } from '../models';
import { _ } from 'entcore';

export interface LibraryControllerScope {
    root: Root
    blog: Blog
    folder: Folder
    currentFolder: Folder | Root
    filters: typeof Filters
    displayLib: {
        data: any
        targetFolder: Folder
        searchBlogs: string
        lightbox: {
            manageBlogs: boolean,
            properties: boolean
        }
    }
    addBlog(): void;
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
    openPublish(): void;
    createFolder(): void;
    manageBlogsView(blog: Blog): void
    removeSelection(): void;
    removeBlogs(): void;
    duplicateBlogs(): void;
    closeManageBlogs(): void;
    createBlogView(): void;
    viewBlog(blog: Blog): void;
    openFolder(folder: Folder): void;
    selectionContains(folder: Folder): boolean;
    dropTo(targetItem: string | Folder, $originalEvent): void;
    //
    $apply: any
}
export function LibraryDelegate($scope: LibraryControllerScope, $rootScope, $location) {
    $scope.displayLib = {
        data: undefined,
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
    $scope.blog = new Blog();
    $scope.blog.visibility = 'PRIVATE';
    $scope.filters = Filters;
    $scope.filters.protected = true;

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
        return _.find(folder.ressources.sel.selected, (w: Blog) => !w.myRights[right]) === undefined;
    };

    $scope.saveProperties = () => {
        $scope.lightbox('properties');
        $scope.blog.save();
    }

    $scope.editBlogProperties = () => {
        $scope.blog = $scope.currentFolder.selection[0] as Blog;
        $scope.lightbox('properties');
    };

    $scope.openFolder = (folder) => {
        template.open('library/folder-content', 'library/folder-content');
        $scope.currentFolder = folder;
        $scope.currentFolder.sync();
    };

    $scope.openPublish = async () => {
        $scope.lightbox('showPublish');
        $scope.blog = $scope.currentFolder.selection[0] as Blog;
    };

    $scope.createFolder = async () => {
        $scope.folder.parentId = $scope.currentFolder._id;
        $scope.displayLib.lightbox['newFolder'] = false;
        $scope.currentFolder.children.push($scope.folder);
        await $scope.folder.save();
        $scope.folder = new Folder();
    };

    $scope.removeSelection = async () => {
        $scope.lightbox('confirmRemove');
        await $scope.currentFolder.removeSelection();
        $scope.$apply();
    }


    $scope.removeBlogs = () => {
        $scope.currentFolder.selection.forEach(function (blog) {
            blog.remove();
        });
        $scope.currentFolder.removeSelection();
        notify.info('blog.removed');
        $scope.closeLightbox('confirmRemovePage');
    };
    $scope.openTrash = () => {
        template.open('library/folder-content', 'library/trash');
        $scope.currentFolder = Folders.trash;
        Folders.trash.sync();
    };

    $scope.openRoot = () => {
        template.open('library/folder-content', 'library/folder-content');
        $scope.currentFolder = Folders.root;
        Folders.root.sync();
    };

    $scope.createBlogView = () => {
        $scope.blog = new Blog();
        $scope.blog.visibility = 'PRIVATE';
        $scope.lightbox('newBlog');
    };

    $scope.viewBlog = (blog: Blog) => {
        $location.path('/view/' + blog._id);
    };

    $scope.open = (item: Blog | Folder) => {
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

    $scope.manageBlogsView = (blog: Blog) => {
        $scope.lightbox('managePages');
        $scope.blog = blog;
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
}