import { Behaviours, routes, template, idiom, http, notify, ng, angular } from 'entcore'
import { LibraryDelegate } from './controllers/library';

function safeApply(that) {
	return new Promise((resolve, reject) => {
		let phase = that.$root.$$phase;
		if (phase === '$apply' || phase === '$digest') {
			if (resolve && (typeof (resolve) === 'function')) resolve();
		} else {
			if (resolve && (typeof (resolve) === 'function')) that.$apply(resolve);
			else that.$apply();
		}
	});
}
export const blogController = ng.controller('BlogController', ['$scope', 'route', 'model', '$location', '$rootScope', ($scope, route, model, $location, $rootScope) => {
	LibraryDelegate($scope,$rootScope, $location)
	$scope.template = template;
	template.open('filters', 'filters');
	template.open('edit-post', 'edit-post');
	template.open('read-post', 'read-post');
	template.open('share', "share");

	$scope.me = model.me;
	$scope.blogs = model.blogs;
	$scope.comment = new Behaviours.applicationsBehaviours.blog.model.Comment();
	$scope.lang = idiom;
	var viewPostFactory = function (modal) {
		return function (params) {
			template.close('create-post');
			model.blogs.deselectAll();

			var openLightbox = function (post) {
				if (post) {
					modal && template.open('read-post-modal', 'read-post-modal');
					template.open('read-post', 'read-post');
					$scope.post.slided = true;
					$scope.currPost = $scope.post._id;
					$scope.display.postNotFound = false;
				} else {
					modal && template.open('read-post', 'e404');
					$scope.display.postNotFound = true;
				}
				//
				modal && ($scope.display.postRead = true);
				safeApply($scope);
			}
			var openPost = function (post) {
				$scope.post = post;
				$scope.post.open(function () {
					post.comments.sync();
					openLightbox(post);
					initPostCounter(params.blogId);
				}, function () {
					openLightbox(null);
				});
			}
			var syncOnePostIfNeeded = function () {
				var founded = null;
				$scope.blog.posts.forEach(function (post) {
					post.slided = false;
					if (post._id === params.postId) {
						founded = post;
					}
				})
				if (founded) {
					openPost(founded);
				} else {
					$scope.blog.posts.syncOnePost(function () {
						if ($scope.blog.posts.length() > 0) {
							$scope.blog.posts.forEach(function (post) {
								if (post._id === params.postId) openPost(post);
							})
						} else {
							template.open('main', 'e404');
							openLightbox(null);
						}
					}, params.postId);
				}
			}
			var syncPostsIfNeeded = function () {
				if ($scope.blog.posts.length() < 1) {
					$scope.blog.posts.syncPosts(function () {
						syncOnePostIfNeeded();
					});
				} else {
					syncOnePostIfNeeded();
				}
			}
			var syncBlogIfNeeded = function () {
				$scope.blog = model.blogs.findWhere({ _id: params.blogId });
				if (!$scope.blog) {
					var data = { _id: params.blogId };
					$scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog(data);
					$scope.blog.open(function () {
						model.blogs.push($scope.blog);

						$scope.blog = model.blogs.findWhere({ _id: params.blogId });
						if (!$scope.blog) {
							template.open('main', 'e404');
							openLightbox(null);
						} else {
							template.open('main', 'blog');
							syncPostsIfNeeded();
						}
					}, function () {
						template.open('main', 'e404');
						openLightbox(null);
					});
				} else {
					template.open('main', 'blog');
					syncOnePostIfNeeded();
				}
			}
			syncBlogIfNeeded();
		}
	}
	route({
		viewBlog: function (params) {
			model.blogs.deselectAll();

			$scope.blog = model.blogs.findWhere({ _id: params.blogId });

			if (!$scope.blog) {
				var data = { _id: params.blogId };
				$scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog(data);
				$scope.blog.open(function () {
					model.blogs.push($scope.blog);
					$scope.blog = model.blogs.findWhere({ _id: params.blogId });

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
		print: function (params) {
			let data = { 
				_id: params.blogId,
			}
			let postId;
			if (params.postId) {
				postId = params.postId;
			}
			$scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog(data);
			if (params.comments === 'true')
				$scope.showComments = true;
			$scope.blog.open(function () {
				$scope.blog.posts.syncAllPosts(function () {
					if (!postId) {
						$scope.blog.posts.all = $scope.blog.posts.all.filter(p => p.state == "PUBLISHED");
					} else {
						$scope.blog.posts.all = $scope.blog.posts.all.filter(p => p._id === postId);
					}
					let countDown = $scope.blog.posts.length();
					let onFinish = function () {
						if (--countDown <= 0) {
							setTimeout(function () {
								window.print()
							}, 1000);
						}
					};
					if (countDown === 0) {
						onFinish();
					}
					$scope.blog.posts.forEach(async function (post) {
						if (params.comments)
							await post.comments.sync();
						post.open(function () {
							onFinish();
						})
					})

				});

			}, function () {
				template.open('main', 'e404');
			});
		},
		viewPostInline: viewPostFactory(false),
		viewPostModal: viewPostFactory(true),
		newArticle: function (params) {
			$scope.post = new Behaviours.applicationsBehaviours.blog.model.Post();

			$scope.blog = model.blogs.findWhere({ _id: params.blogId });
			if (!$scope.blog) {
				template.open('main', 'e404');
			} else {
				template.open('main', 'blog');
				template.open('create-post', 'create-post');
			}
		},
		list: function () {
			model.blogs.deselectAll();
			template.open('main', 'blogs-list');
			$scope.display.filters.submitted = true;
			$scope.display.filters.draft = true;
			$scope.display.filters.published = true;
			$scope.display.filters.all = true;
			$scope.display.postSearch = '';
			//dont load blog using pagination (see library.ts)
			//model.blogs.syncPag(function () { $scope.$apply(); }, false, $scope.display.search);
		},
		editBlog: function (params) {
			$scope.blog = model.blogs.findWhere({ _id: params.blogId });
			const callback = ()=>{
				if ($scope.blog) {
					template.open('main', 'edit-blog');
				}
				else {
					$scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog();
					template.open('main', 'edit-blog');
				}
			}
			if(params.blogId=="new"){
				callback();
			}else if($scope.blog){
				callback();
			}else{
				const data = { _id: params.blogId };
				$scope.blog = new Behaviours.applicationsBehaviours.blog.model.Blog(data);
				$scope.blog.open(()=>{
					callback();
				})
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

	function initPostCounter(blogId) {
		model.blogs.counterPost(blogId, function (counters) {
			$scope.display.countPublished = counters.countPublished;
			$scope.display.countDraft = counters.countDraft;
			$scope.display.countSubmitted = counters.countSubmitted;
			$scope.display.countAll = counters.countAll;
			$scope.$apply();
		});
	}

	$scope.resetSearching = function () {
		$scope.display.searching = true;
	};

	$scope.launchSearchingPost = function (mysearch, event) {
		event.stopPropagation();
		pSearchingPost(mysearch);
	};

	$scope.searchingPost = function () {
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
		}, false, mysearch, $scope.display.filters);
	};

	$scope.openClosePost = function (blog, post) {
		if (post.slided) {
			post.slided = false;
			$scope.redirect('/view/' + blog._id);
		}
		else {
			$scope.redirect('/detail/' + blog._id + '/' + post._id);
		}
	}

	$scope.openFirstPost = function (blog, post) {
		//if a post is already slided, do nothing
		var found = $scope.blog.posts.all.filter(p => p.slided === true);
		if (!found || found.length === 0) {
			post.slided = true;
			post.open(function () {
				$scope.$apply();
			})
			$scope.currPost = post._id;
		}
	}

	$scope.display = {
		postRead: false,
		postNotFound: false,
		filters: {
			submitted: true,
			draft: true,
			published: true,
			all: true
		},
		searching: false
	}

	$scope.cancel = function () {
		history.back();
	}

	$scope.count = function (state) {
		return $scope.blog.posts.where({ state: state }).length;
	}

	$scope.switchAll = function () {
		for (let filter in $scope.display.filters) {
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

	$scope.checkAll = function () {
		$scope.display.filters.all = true;
		for (let filter in $scope.display.filters) {
			$scope.display.filters.all = $scope.display.filters[filter] && $scope.display.filters.all;
		}
		$scope.blog.posts.syncPosts(function () {
			$scope.blog.posts.forEach(function (post) {
				post.comments.sync();
			});
		}, false, $scope.display.postSearch, $scope.display.filters);
	};

	$scope.showEditPost = function (blog, post) {
		post.editing = true;
		if (!post.slided) {
			$scope.redirect('/detail/' + blog._id + '/' + post._id);
		}
	};

	$scope.cancelEditing = function (post) {
		post.editing = false;
		post.content = post.data.content;
		post.title = post.data.title;
	};

	$scope.saveDraft = function () {
		if (checkPost($scope.post)) {
			$scope.post.publishing = true;
			$scope.post.save(function () {
				$location.path('/view/' + $scope.blog._id);
			}, $scope.blog, 'DRAFT');
			notify.info('draft.saved');
		}
	};

	$scope.saveOrCreates = function (post) {
		if (checkPost($scope.post)) {
			post.save(function () {
				initPostCounter(post.blogId);
				post.editing = false;
			});
		}
	};

	$scope.saveModifications = function (post) {
		if (checkPost(post)) {
			post.saveModifications(function (state) {
				initPostCounter(post.blogId);
				post.state = state;
				post.editing = false;
			});
		}
	};

	function checkPost(post): boolean {
		let checked = true;
		if (!post.title) {
			notify.error('title.empty');
			checked = false;
		} else if (!post.content || post.content.replace(/<[^>]*>/g, '') === '') {
			notify.error('post.empty');
			checked = false;
		}

		return checked;
	};

	$scope.savePublishedPost = function () {
		if (checkPost($scope.post)) {
			if ($scope.post._id !== undefined) {
				$scope.post.publish(function () {
					$scope.post.publishing = true;
					initPostCounter($scope.post.blogId);
				});
			}
			else {
				$scope.post.save(function () {
					$scope.post = $scope.blog.posts.first();
					$scope.post.publishing = true;
					$location.path('/detail/' + $scope.post.blogId + '/' + $scope.post._id);
				}, $scope.blog, 'PUBLISHED');
			}
		}
	};

	$scope.publishPost = function (post) {
		post.publish(function () {
			initPostCounter(post.blogId);
		}, post.author.userId == model.me.userId);
	};

	$scope.republish = function (blog, post) {
		post.republish(function () {
			blog.posts.syncAllPosts(function () {});
		});
	}

	function initMaxResults() {
		$scope.maxResults = 3;
	}
	initMaxResults()
	$scope.addResults = function () {
		$scope.maxResults += 3;
	}

	$scope.updatePublishType = function () {
		model.blogs.selection().forEach(function (blog) {
			blog['publish-type'] = $scope.display.publishType;
			blog.save();
		})
	}

	$scope.removePost = function (post) {
		post.remove(function () {
			initPostCounter(post.blogId);
			$scope.blog.posts.syncPosts(function () {
				$scope.blog.posts.forEach(function (post) {
					post.comments.sync();
				})
			}, false, $scope.display.postSearch, $scope.display.filters)
		});
	}

	$scope.redirect = function (path) {
		$location.path(path);
	}

	$scope.loadPosts = function () {
		$scope.blog.posts.syncPosts(function () {
			$scope.blog.posts.forEach(function (post) {
				post.comments.sync();
			})
		}, true, $scope.display.postSearch, $scope.display.filters)
	}

	$scope.postComment = function (comment, post) {
		post.comment(comment);
		$scope.comment = new Behaviours.applicationsBehaviours.blog.model.Comment();
	}

	$scope.updateComment = function (comment, post) {
		post.updateComment(comment);
	}

	$scope.orderBlogs = function (blog) {
		let discriminator = 0;
		if (blog.myRights.editBlog)
			discriminator = 2;
		else if (blog.myRights.createPost)
			discriminator = 1;
		return parseInt(discriminator + '' + blog.modified.$date);
	}

	$scope.display.showPrintComments = false;

	$scope.print = function (printComments) {
		if ($scope.blog.posts.some(post => post.comments.all.length > 0) && !$scope.display.showPrintComments) {
			$scope.display.showPrintComments = true;
			$scope.display.printPost = false;
		}
		else {
			$scope.display.showPrintComments = false;
			window.open(`/blog/print/blog#/print/${$scope.blog._id}?comments=${printComments}`, '_blank');
		}
	}
	$scope.printPost = function (post, printComments) {
		if (post) {
			$scope.postToPrint = post;
		}
		if ($scope.postToPrint.comments.all.length > 0 && !$scope.display.showPrintComments) {
			$scope.display.printPost = true;
			$scope.display.showPrintComments = true;
		}
		else {
			$scope.display.showPrintComments = false;
			window.open(`/blog/print/blog#/print/${$scope.blog._id}/post/${$scope.postToPrint._id}?comments=${printComments}`, '_blank');
		}
	}
	$scope.isCloseConfirmLoaded = function () {
		return angular.element('share-panel .share').scope()
			&& angular.element('share-panel .share').scope().display.showCloseConfirmation;
	}

}]);
