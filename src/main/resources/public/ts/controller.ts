import { Behaviours, routes, template, idiom, http, notify } from 'entcore/entcore'

routes.define(function($routeProvider){
	$routeProvider
		//fixme don't work with direct access from front route
		.when('/view/:blogId', {
			action: 'viewBlog'
		})
		.when('/edit/:blogId', {
			action: 'editBlog'
		})
		.when('/new-article/:blogId', {
			action: 'newArticle'
		})
		.when('/list-blogs', {
			action: 'list'
		})
		.when('/view/:blogId/:postId', {
			action: 'viewPost'
		})
		.when('/print/:blogId', {
			action: 'print'
		})
		.otherwise({
			redirectTo: '/list-blogs'
		})
});

export function BlogController($scope, route, model, $location){

	$scope.template = template;
	template.open('filters', 'filters');
	template.open('edit-post', 'edit-post');
	template.open('read-post', 'read-post');
	template.open('share', "share");

	$scope.me = model.me;
	$scope.blogs = model.blogs;
	$scope.comment = new Behaviours.applicationsBehaviours.blog.model.Comment();
	$scope.lang = idiom;

	route({
		viewBlog: function(params){
			model.blogs.deselectAll();

			$scope.blog = model.blogs.findWhere({_id: params.blogId});

			if (!$scope.blog) {				
				var data = {_id: params.blogId};
				$scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog(data);
				$scope.blog.open(function () {
						model.blogs.push($scope.blog);
						$scope.blog = model.blogs.findWhere({_id: params.blogId});

						if (!$scope.blog) {
							template.open('main', 'e404');
						} else {
							template.open('main', 'blog');
							template.close('create-post');

							if ($scope.blog.posts.length() < 1) {
								$scope.blog.posts.syncPosts(function () {
									openFirstPostAndCounter(params.blogId);
									$scope.blog.posts.forEach(function (post) {
										post.comments.sync();
									})
								})
							} else {
								openFirstPostAndCounter(params.blogId);
							}
						}
					}, function () {
						template.open('main', 'e404');
					}
				);
			} else {
				template.open('main', 'blog');
				template.close('create-post');

				if ($scope.blog.posts.length() < 1) {
					$scope.blog.posts.syncPosts(function () {
						openFirstPostAndCounter(params.blogId);

						$scope.blog.posts.forEach(function (post) {
							post.comments.sync();

						})
					})
				} else {
					openFirstPostAndCounter(params.blogId);
				}
			};
		},
		print: function(params){
			var data = {_id:params.blogId}
			$scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog(data);
			$scope.blog.open(function() {
				$scope.blog.posts.syncAllPosts(function () {

					let countDown = $scope.blog.posts.length();
					let onFinish = function () {
						if (--countDown <= 0) {
							$scope.$apply();
							setTimeout(function () {
								window.print()
							}, 1000);
						}
					};

					if (countDown === 0) {
						onFinish();
					}
					$scope.blog.posts.forEach(function (post) {
						post.open(function () {
							onFinish();
						})
					})

				});
			}, function() {
				template.open('main', 'e404');
			});
		},
		viewPost: function(params){
			template.close('create-post');
			model.blogs.deselectAll();

			$scope.blog = model.blogs.findWhere({ _id: params.blogId });

			if (!$scope.blog) {
				var data = {_id: params.blogId};
				$scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog(data);
				$scope.blog.open(function () {
					model.blogs.push($scope.blog);

					$scope.blog = model.blogs.findWhere({_id: params.blogId});
					if (!$scope.blog) {
						template.open('main', 'e404');
					} else {
						template.open('main', 'blog');
						if ($scope.blog.posts.length() < 1) {
							$scope.blog.posts.syncOnePost(function () {
								console.log('viewPost length ' + $scope.blog.posts.length());
								if ($scope.blog.posts.length() > 0) {
									$scope.blog.posts.forEach(function (post) {
										post.comments.sync();
										$scope.post = post;
										$scope.post.open(function () {
											$scope.post.slided = true;
											$scope.currPost = $scope.post._id;
											initPostCounter(params.blogId);
										});
									})
								} else {
									template.open('main', 'e404');
								}
							}, params.postId);
						}
					}

				}, function () {
					template.open('main', 'e404');
				});
			} else {
				template.open('main', 'blog');
				$scope.blog.posts.forEach(function(post){
					if(post._id === params.postId){
						$scope.currPost = post._id;
						post.open(function(){
							post.slided = true;
							initPostCounter(params.blogId);
						})
					} else
						post.slided = false;
				})
			}
		},
		newArticle: function(params){
			$scope.post = new Behaviours.applicationsBehaviours.blog.model.Post();

			$scope.blog = model.blogs.findWhere({ _id: params.blogId });
			if(!$scope.blog){
				template.open('main', 'e404');
			} else {
				template.open('main', 'blog');
				template.open('create-post', 'create-post');
			}
		},
		list: function(){
			model.blogs.deselectAll();
			template.open('main', 'blogs-list');
			$scope.display.filters.submitted = true;
			$scope.display.filters.draft = true;
			$scope.display.filters.published = true;
			$scope.display.filters.all = true;
			$scope.display.postSearch = '';

			model.blogs.syncPag(function() {$scope.$apply();}, false, $scope.display.search);
		},
		editBlog: function(params){
			$scope.blog = model.blogs.findWhere({ _id: params.blogId });
			if($scope.blog){
				template.open('main', 'edit-blog');
			}
			else{
				$scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog();
				template.open('main', 'edit-blog');
			}
		}
	});
	
	function openFirstPostAndCounter(blogId) {
		$scope.post = $scope.blog.posts.first();
		if ($scope.post) {
			$scope.post.open(function () {
				$scope.post.slided = true;
				$scope.currPost = $scope.post._id;
				initPostCounter(blogId);
			});
		} else {
			initPostCounter(blogId);
		}
	}

	function initPostCounter(blogId){
		model.blogs.counterPost(blogId, function(counters) {
			$scope.display.countPublished = counters.countPublished;
			$scope.display.countDraft = counters.countDraft;
			$scope.display.countSubmitted = counters.countSubmitted;
			$scope.display.countAll = counters.countAll;
			$scope.$apply();
		});
	}
	
	$scope.resetSearching = function() {
		$scope.display.searching = true;
	};

	$scope.launchSearchingPost = function(mysearch, event) {
		event.stopPropagation();
		pSearchingPost(mysearch);
	};

	$scope.searchingPost = function() {
		pSearchingPost($scope.display.postSearch);
	};

	function pSearchingPost(mysearch) {
		$scope.display.searching = true;
		$scope.blog.posts.syncPosts(function () {
			$scope.display.searching = false;
			let counter = $scope.blog.posts.length();
			if (counter === 0) $scope.$apply();
			$scope.blog.posts.forEach(function (post) {
				post.comments.sync();
				if (--counter <= 0) {
					$scope.$apply();
				}
			})
		},false, mysearch, $scope.display.filters);
	};

	$scope.launchSearching = function(mysearch, event) {
		$scope.display.searching = true;
		event.stopPropagation();
		model.blogs.syncPag(function () {$scope.display.searching = false; $scope.$apply();}, false, mysearch);
	};

	$scope.searching = function() {
		$scope.display.searching = true;
		model.blogs.syncPag(function () {$scope.display.searching = false; $scope.$apply();}, false, $scope.display.search);
	};

	$scope.openClosePost = function (blog, post) {
	    if (post.slided) {
	        post.slided = false;
	        $scope.redirect('/view/' + blog._id);
	    }
	    else {
	        $scope.redirect('/view/' + blog._id + '/' + post._id);
	    }
	}

    $scope.openFirstPost = function(blog, post){
        post.slided = true;
        post.open(function(){
            $scope.$apply();
        })
		$scope.currPost = post._id;
    }

	$scope.display = {
		filters: {
			submitted: true,
			draft: true,
			published: true,
			all: true
		},
		searching: false
	}

	$scope.saveBlog = function(){
		$scope.blog.save(function(){
			model.blogs.syncPag();
		});
		history.back();
	}

	$scope.cancel = function(){
		history.back();
	}

	$scope.count = function(state){
		return $scope.blog.posts.where({ state: state }).length;
	}

	$scope.switchAll = function(){
		for(let filter in $scope.display.filters){
			$scope.display.filters[filter] = $scope.display.filters.all;
		}

		if ($scope.display.filters.all) {
			$scope.blog.posts.syncPosts(function () {
				$scope.blog.posts.forEach(function (post) {
					post.comments.sync();
				})
			}, false, $scope.display.postSearch, $scope.display.filters);
		} else {
			$scope.blog.posts.all = [];
		}
	}

	$scope.checkAll = function(){
		$scope.display.filters.all = true;
		for(let filter in $scope.display.filters){
			$scope.display.filters.all = $scope.display.filters[filter] && $scope.display.filters.all;
		}

		if (!$scope.display.filters.all && ($scope.display.filters.submitted || $scope.display.filters.draft || $scope.display.filters.published)) {
			$scope.blog.posts.syncPosts(function () {
				$scope.blog.posts.forEach(function (post) {
					post.comments.sync();
				});
			}, false, $scope.display.postSearch, $scope.display.filters);
		} else {
			$scope.blog.posts.all = [];
		}
	};

	$scope.showEditPost = function(blog, post){
		post.editing = true;
		if (!post.slided) {
			$scope.redirect('/view/' + blog._id + '/' + post._id);
		}
	};

	$scope.cancelEditing = function (post) {
	    post.editing = false;
	    post.content = post.data.content;
		post.title = post.data.title;
	};

	$scope.saveDraft = function(){
		if (checkPost($scope.post)) {
			$scope.post.publishing = true;
			$scope.post.save(function () {
				$location.path('/view/' + $scope.blog._id);
			}, $scope.blog, 'DRAFT');
			notify.info('draft.saved');
		}
	};

	$scope.saveOrCreates = function(post){
		if (checkPost($scope.post)) {
			post.save(function() {
				initPostCounter(post.blogId);
				post.editing = false;
			});
		}
	};

	$scope.saveModifications = function(post){
		if (checkPost(post)) {
			post.saveModifications(function(state) {
				initPostCounter(post.blogId);
				post.state = state;
				post.editing = false;
			});
		}
	};

	function checkPost(post):boolean {
		let checked = true;
		if(!post.title){
			notify.error('title.empty');
			checked = false;
		} else if (!post.content || post.content.replace(/<[^>]*>/g, '') === '') {
			notify.error('post.empty');
			checked = false;
		}

		return checked;
	};

	$scope.savePublishedPost = function(){
		if (checkPost($scope.post)) {
			if ($scope.post._id !== undefined) {
				$scope.post.publish(function() {
					$scope.post.publishing=true;
					initPostCounter($scope.post.blogId);
				});
			}
			else {
				$scope.post.save(function () {
					$scope.post =  $scope.blog.posts.first();
					$scope.post.publishing=true;
					$location.path('/view/' + $scope.post.blogId+ '/' + $scope.post._id);
				}, $scope.blog, 'PUBLISHED');
			}
		}
	};

	$scope.publishPost = function(post) {
		post.publish(function() {
			initPostCounter(post.blogId);
		});
	};

	function initMaxResults(){
		$scope.maxResults = 3;
	}
	initMaxResults()
	$scope.addResults = function(){
		$scope.maxResults += 3;
	}

	$scope.updatePublishType = function(){
		model.blogs.selection().forEach(function(blog){
			blog['publish-type'] = $scope.display.publishType;
			blog.save();
		})
	}

	$scope.removePost = function(post){
		post.remove(function () {
			initPostCounter(post.blogId);
			$scope.blog.posts.syncPosts(function () {
				$scope.blog.posts.forEach(function (post) {
					post.comments.sync();
				})
			}, false, $scope.display.postSearch, $scope.display.filters)
		});
	}

	$scope.removeBlog = function(){
		model.blogs.remove($scope.blog);
		$location.path('/list-blogs');
	}

	$scope.removeBlogs = async function(){
		await $scope.blogs.removeSelection();
		model.blogs.syncPag();
	}

	$scope.redirect = function(path){
		$location.path(path);
	}

	$scope.loadBlogs = function(){
		model.blogs.syncPag(undefined, true, $scope.display.search);
	}

	$scope.loadPosts = function() {
		$scope.blog.posts.syncPosts(function () {
			$scope.blog.posts.forEach(function (post) {
				post.comments.sync();
			})
		}, true, $scope.display.postSearch, $scope.display.filters)
	}

	$scope.shareBlog = function(){
		$scope.display.showShare = true;
		let same = true;
		let publishType = model.blogs.selection()[0]['publish-type'];
		model.blogs.selection().forEach(function(blog){
			same = same && (blog['publish-type'] === publishType);
		});
		if(same){
			$scope.display.publishType = publishType;
		}
		else{
			$scope.display.publishType = undefined;
		}
	}

	$scope.postComment = function(comment, post){
		post.comment(comment);
		$scope.comment = new Behaviours.applicationsBehaviours.blog.model.Comment();
	}

	$scope.orderBlogs = function(blog){
		let discriminator = 0;
		if(blog.myRights.editBlog)
			discriminator = 2;
		else if(blog.myRights.createPost)
			discriminator = 1;
		return parseInt(discriminator + '' + blog.modified.$date);
	}

}