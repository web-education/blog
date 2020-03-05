import { idiom, notify, template } from 'entcore';
import { BaseFolder, Blog, Filters, Folder, Folders, Root, Trash } from '../models';
import { BlogModel } from './commons';

export interface LibraryControllerScope {
    root: Root
    blog: BlogModel
    folder: Folder
    currentFolder: Folder | Root
    filters: typeof Filters
	empty: boolean;
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
    saveProperties(): Promise<void>;
    can(right: string): boolean
    searchBlog(blog: Blog): boolean;
    searchFolder(folder: Folder): boolean;
    createFolder(): void;
    trashSelection(): void;
    tryTrashSelection(): void;
    isABlog(item: Blog | Folder): boolean;
    removeSelection(): void;
    duplicateBlogs(): void;
    createBlogView(): void;
    viewBlog(blog: Blog, ev?: Event): void;
    openFolder(folder: Folder): void;
    openTrashFolder(folder: Folder): void;
    selectionContains(folder: Folder): boolean;
    dropTo(targetItem: string | Folder, $originalEvent): void;
    removeBlog(): void;
    isTrashFolder(): boolean;
    hasFiltersActive(): boolean;
    printBlog(blog: Blog, printComments: boolean): void;
    showBlogLoader():boolean
    showEmptyScreen():boolean
    showEmptyScreenInFolder():boolean
    showEmptyScreenSearch():boolean
    showEmptyScreenFilter():boolean
    showEmptyScreenTrash():boolean
    showEmptyScreenTrashSearch ():boolean
    showEmptyScreenTrashFilter ():boolean
    //
    $apply: any
    display: {
        warningDuplicate?: boolean
        publishType?: 'IMMEDIATE' | 'RESTRAINT'
        confirmRemoveBlog?: boolean
        confirmRemoveBlogsPublic?: boolean
    }
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
    };

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

    $scope.hasFiltersActive = () => {
        return $scope.filters.mine && $scope.filters.public && $scope.filters.shared
    }

    template.open('library/create-blog', 'library/create-blog');
    template.open('library/toaster', 'library/toaster');
    template.open('library/publish', 'library/publish');
    template.open('library/properties', 'library/properties');
    template.open('library/move', 'library/move');
    template.open('library/share', "library/share");

    BaseFolder.eventer.on('refresh', () => $scope.$apply());
    Blog.eventer.on('save', () => $scope.$apply());

    $rootScope.$on('share-updated', async (event, changes) => {
        for (let blog of $scope.currentFolder.selection) {
            await (blog as Blog).sync();
        }

        $scope.$apply();
    });

    const resetSelection = () => {
        $scope.currentFolder.deselectAll();
    };
    $scope.printBlog = function(blog: Blog, printComments: boolean) {
        if(blog){
            window.open(`/blog/print/blog#/print/${blog._id}?comments=${printComments}`, '_blank');
        }
    }

    $scope.searchBlog = (item: Blog) => {
        return !$scope.displayLib.searchBlogs || idiom.removeAccents(item.title.toLowerCase()).indexOf(
            idiom.removeAccents($scope.displayLib.searchBlogs).toLowerCase()
        ) !== -1;
    };

    $scope.isTrashFolder = () => {
        return $scope.currentFolder instanceof Trash;
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

    $scope.saveProperties = (): Promise<void> => {

        return new Promise<void>(function(resolve, reject)
        {
            $scope.display.warningDuplicate = false;
            $scope.lightbox('properties');
            //adapt old model to new
            const blog = Folders.root.findRessource($scope.blog._id) || new Blog();
            const isNew = !blog._id;
            blog.fromJSON($scope.blog.toJSON() as any);

            blog.save().then(function()
            {
                if(isNew && $scope.currentFolder && $scope.currentFolder._id)
                    return blog.moveTo($scope.currentFolder as Folder);
                else
                    return null;
            }).then(function()
            {
                if (isNew) {
                    if (blog.visibility == "PUBLIC") {
                        Filters.public = true;
                    } else {
                        Filters.mine = true;
                    }
                }
                resolve();
            }).then(function()
            {
                $location.path("/list-blogs");
                if ($scope.currentFolder)
                    return $scope.currentFolder.sync();
                else
                    return null;
            }).then(function()
            {
                $scope.$apply();
            })
            .catch(function(e)
            {
                if (e.response && e.response.status == 409) {
                    $scope.display.warningDuplicate = true;
                    $scope.lightbox("warningEditBlog");
                    $scope.$apply();
                    resolve();
                } else {
                    console.error(e);
                    reject();
                }
            });
        });
    };

    $scope.removeBlog = async function () {
        const blog = Folders.root.findRessource($scope.blog._id);
        if (blog) {
            await blog.remove();
        }
        $location.path('/list-blogs');
        Folders.onChange.next(!((await Folders.ressources()).length || (await Folders.folders()).length)); // ICI
    };

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

    $scope.openTrashFolder = (folder) => {
        resetSelection();
        template.open('library/folder-content', 'library/trash');
        $scope.currentFolder = folder;
        $scope.currentFolder.sync();
    };

    $scope.createFolder = async () => {
        $scope.folder.parentId = $scope.currentFolder._id;
        $scope.displayLib.lightbox['newFolder'] = false;
        $scope.currentFolder.children.push($scope.folder);
        await $scope.folder.save();
        $scope.folder = new Folder();
        Folders.onChange.next(!((await Folders.ressources()).length || (await Folders.folders()).length)); // ICI
    };

    const doTrashSelection = async () => {
        $scope.closeLightbox('confirmRemove');
        $scope.display.confirmRemoveBlogsPublic = false;
        await $scope.currentFolder.trashSelection();
        await $scope.currentFolder.sync();
        await Folders.trash.sync();
        $scope.$apply();
        notify.info('blog.selection.trashed');
    };

    $scope.tryTrashSelection = async () => {
        const founded = $scope.currentFolder.selection.filter(f => f instanceof Blog).map(f => f as Blog).findIndex(f => f.visibility == "PUBLIC");
        if (founded > -1) {
            $scope.display.confirmRemoveBlogsPublic = true;
        } else {
            await doTrashSelection();
        }
    };

    $scope.isABlog = (item: Blog | Folder): boolean => {
        return item instanceof Blog;
    };

    $scope.trashSelection = async () => {
        await doTrashSelection();
    };

    $scope.removeSelection = async () => {
        $scope.closeLightbox('confirmRemove');
        await $scope.currentFolder.removeSelection();
        $scope.$apply();
        notify.info('blog.selection.removed');
    };

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
        } else {
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
    };

    $scope.move = async () => {
        $scope.lightbox('move');
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
        await $scope.currentFolder.restoreSelection();
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
        } else {
            $scope.display.publishType = undefined;
        }
    };

    $scope.showBlogLoader = () => {
        return !$scope.root.syncFolder || !$scope.root.syncResource;
    }

    $scope.showEmptyScreen = () => {
        if($scope.showBlogLoader()) return false;
        return $scope.empty;
    }

    $scope.showEmptyScreenInFolder = () => {
        if($scope.showBlogLoader()) return false;
        return $scope.hasFiltersActive() 
                && !$scope.currentFolder.ressources.all.length 
                && !$scope.currentFolder.children.all.length;
    }

    $scope.showEmptyScreenSearch = () => {
        if($scope.showBlogLoader()) return false;
        return $scope.displayLib.searchBlogs 
                && ($scope.currentFolder.ressources.all.length || $scope.currentFolder.children.all.length) 
                && $scope.currentFolder.children.all.filter($scope.searchFolder).length == 0 
                && $scope.currentFolder.ressources.all.filter($scope.searchBlog).length == 0
    }

    $scope.showEmptyScreenFilter = () => {
        if($scope.showBlogLoader()) return false;
        return !(
                    $scope.displayLib.searchBlogs 
                    && $scope.currentFolder.children.all.filter($scope.searchFolder).length == 0 
                    && $scope.currentFolder.ressources.all.filter($scope.searchBlog).length == 0
                )
                && !$scope.hasFiltersActive() 
                && !$scope.currentFolder.ressources.filtered.length
                && !$scope.currentFolder.children.all.length ;
    }

    $scope.showEmptyScreenTrash = () =>{
        if($scope.showBlogLoader()) return false;
        return !$scope.currentFolder.ressources.all.length 
                && !$scope.currentFolder.children.all.length;
    }

    $scope.showEmptyScreenTrashSearch = () => {
        if($scope.showBlogLoader()) return false;
        return $scope.displayLib.searchBlogs 
                && ($scope.currentFolder.ressources.all.length || $scope.currentFolder.children.all.length) 
                && $scope.currentFolder.children.all.filter($scope.searchFolder).length == 0 
                && $scope.currentFolder.ressources.all.filter($scope.searchBlog).length == 0;
    }

    $scope.showEmptyScreenTrashFilter = () => {
        if($scope.showBlogLoader()) return false;
        return !(
                    $scope.displayLib.searchBlogs 
                    && $scope.currentFolder.children.all.filter($scope.searchFolder).length == 0 
                    && $scope.currentFolder.ressources.all.filter($scope.searchBlog).length == 0
                )
                && !$scope.hasFiltersActive() 
                && !$scope.currentFolder.ressources.filtered.length 
                && !(!$scope.currentFolder.ressources.all.length && !$scope.currentFolder.children.all.length);
    }
}