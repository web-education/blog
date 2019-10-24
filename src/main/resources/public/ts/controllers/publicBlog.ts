import { idiom, ng, Behaviours, ui, skin } from 'entcore'
import { BlogModel, PostModel } from './commons';


interface BlogPublicControllerScope {
    display: {
        printPost: boolean
        searching: boolean
        postSearch: string,
        filters: {
            submitted: true,
            draft: false,
            published: true,
            all: false
        }
    }
    currentBlog: BlogModel
    blog: BlogModel;
    lang: typeof idiom;
    showComments:boolean;
    printed: boolean;
    $apply: any;
    replaceAudioVideo(s:string):string
    preparePrint(blog: BlogModel): void
    print(): void;
    loadPosts(): void;
    searchingPost(): void;
    setBlog(blog: BlogModel): void
    viewPostInline(): void
    openClosePost(blog: BlogModel, post: PostModel): void;
    openFirstPost(blog: BlogModel, post: PostModel): void;
    launchSearchingPost(search: string, event?: Event): void;
}

export const blogPublicController = ng.controller('BlogPublicController', ['$scope', '$sce', 'route', 'model', '$location', '$rootScope', ($scope: BlogPublicControllerScope, $sce, route, model, $location, $rootScope) => {
    $scope.display = {
        printPost: false,
        searching: false,
        postSearch: "",
        filters: {
            submitted: true,
            draft: false,
            published: true,
            all: false
        }
    }
    $scope.showComments= false;
    $scope.currentBlog = undefined
    $scope.blog = undefined;
    $scope.lang = idiom;
    $scope.print = () => {
        if ($scope.blog.posts.some(post => post.comments.all.length > 0)) {
            $scope.display.printPost = false;
        }
        else {
            window.open(`/blog/pub/print/${$scope.blog._id}`, '_blank');
        }
    }
    $scope.loadPosts = () => {
        $scope.blog.posts.syncPosts(function () {
        }, true, $scope.display.postSearch, $scope.display.filters, true)
    }
    $scope.searchingPost = () => {
        pSearchingPost($scope.display.postSearch);
    }
    $scope.preparePrint = (blog: BlogModel = (window as any).currentBlog) => {
        $scope.currentBlog = $scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog(blog);
        $scope.blog.posts.syncAllPosts(function () {
            $scope.display.searching = false;
            let counter = $scope.blog.posts.length();
            if (counter === 0) $scope.$apply();
            setTimeout(()=>{
                const imgs = jQuery(document).find("img").toArray();
                for(let img of imgs){
                    (img as any).onerror=(()=>{
                        (img as any).error = true;
                    })
                }
                const isComplete = (img)=>{
                    return img.complete || (img.context && img.context.complete)
                }
                $scope.printed = false;
                const it = setInterval(()=>{
                    const pending = imgs.filter(img=>!(img as any).error && !isComplete(img));
                    if(pending.length == 0){
                        clearInterval(it);
                        if(!$scope.printed){
                            $scope.printed = true;
                            window.print()
                        }
                    }
                },100)
            },1000)
        }, true);
    }
    $scope.replaceAudioVideo = (s: string) => s;
	/*{
		let res =  s &&
		// Audio
		s.replace(/<div class=\"audio-wrapper.*?\/div>/g,"<img src='" + skin.basePath + "img/illustrations/audio-file.png' width='300' height='72'>");
		// Video
		.replace(/<iframe.*?src="(.+?)[\?|\"].*?\/iframe>/g,"<img src='" + skin.basePath + "img/icons/video-large.png' width='135' height='135'><br><a href=\"$1\">$1</a>");
		return $sce.trustAsHtml(res);
	}*/
    $scope.setBlog = (blog: BlogModel = (window as any).currentBlog) => {
        $scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog(blog);
        pSearchingPost("")
    }
    $scope.viewPostInline = function () {
        $location.path("/")
    }
    $scope.openClosePost = (blog: BlogModel, post: PostModel) => {
        $scope.blog.posts.all.forEach(p => p.slided = false);
        post.slided = !post.slided;
        if (post.slided) {
            $location.path('/detail/' + post.blogId + '/' + post._id);
        }
    }
    $scope.openFirstPost = (blog: BlogModel, post: PostModel) => {
        post.slided = true;
    }
    $scope.launchSearchingPost = (search: string, event?: Event) => {
        event.stopPropagation();
        pSearchingPost(search);
    }
    function pSearchingPost(mysearch: string) {
        $scope.display.searching = true;
        $scope.blog.posts.syncPosts(function () {
            $scope.display.searching = false;
            let counter = $scope.blog.posts.length();
            if (counter === 0) $scope.$apply();
        }, false, mysearch, $scope.display.filters, true);
    };
    //cancel effect of infrafront
    if (document.addEventListener) {
        document.addEventListener('DOMContentLoaded', function () {
            setTimeout(function () {
                document.getElementsByTagName('body')[0].style.display = 'block';
            })
        });
    }
}]);