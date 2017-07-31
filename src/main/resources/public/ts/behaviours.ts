import { Behaviours, http as oldHttp, Collection, model } from 'entcore/entcore';
import { _ } from 'entcore/libs/underscore/underscore';
import { moment } from 'entcore/libs/moment/moment';
import http from 'axios';

console.log('blog behaviours file');

export let blogModel: any = {
		Comment: function(data){
			if(data && data.created){
				this.created = moment(this.created.$date);
			}
		},
		Post: function(data){
			let that = this;
			if(data){
				this.created = data.created ? moment(data.created.$date) : moment();
				this.modified = data.modified ? moment(data.modified.$date) : moment();
			}
			this.collection(Behaviours.applicationsBehaviours.blog.model.Comment, {
				sync: '/blog/comments/:blogId/:_id',
				remove: async function(comment){
					await http.delete('/blog/comment/' + that.blogId + '/' + that._id + '/' + comment.id);
					Collection.prototype.remove.call(this, comment);
				}
			});
		},
		Blog: function(data){
			let that = this;
			if(data && data._id){
				this._id = data._id;
				this.owner = data.author;
				this.shortenedTitle = data.title || '';
				if(this.shortenedTitle.length > 40){
					this.shortenedTitle = this.shortenedTitle.substr(0, 38) + '...';
				}
				if(data.thumbnail){
					this.icon = data.thumbnail + '?thumbnail=290x290';
				}
				else{
					this.icon = '/img/illustrations/blog.png';
				}
				this.updateData(data);
                this.fetchPosts = _.map(this.fetchPosts, function(post){
                    return new Behaviours.applicationsBehaviours.blog.model.Post(post);
                });
			}

			this.collection(Behaviours.applicationsBehaviours.blog.model.Post, {
			    syncPosts: function (cb, paginate, search, filters) {
					//for direct resource access (via uri)
					if (paginate && !this.page) {
						paginate = false;
					}
					//end direct access

					if (!paginate) {
						this.page=0;
						this.lastPage = false;
						this.all = [];
					}

					if (this.postsLoading || this.lastPage) {
						return;
					}
					this.postsLoading = true;
					this.lastPage = false;

					if (!search) {
						search = '';
					}

					let jsonParam = {page: this.page, search: search};

					if (filters) {						
						if (!filters.all) {							
							let filterValues = "";
							for (let filter in filters) {
								let filterValue = filters[filter];
								if (filter !== 'all' && filters[filter]) {
									filterValues = (filterValues === "") ? filter.toUpperCase() : filterValues + "," + filter.toUpperCase();
								}
							}

							if (filterValues !== "") {
								jsonParam["states"] = filterValues;
							}
						}
					}

					oldHttp().get('/blog/post/list/all/' + that._id, jsonParam).done(function(posts){
						if(posts.length > 0){
							posts.map(function(item){
								item.blogId = data._id;
								item['publish-type'] = data['publish-type'];
								item['firstPublishDate'] = item['firstPublishDate'] || item['modified'];
								return item;
							});
							this.addRange(posts);
							this.page++;
						}else{
							this.lastPage=true;
						}

						this.postsLoading = false;
                        if(typeof cb === 'function')
                            cb();
					}.bind(this))
			        .e401(function () { })
			        .e404(function () { });
				},
				syncAllPosts: function (cb) {
					if (this.postsLoading) {
						return;
					}
					this.postsLoading = true;
					
					oldHttp().get('/blog/post/list/all/' + that._id).done(function(posts) {
						posts.map(function (item) {
							item.blogId = data._id;
							item['publish-type'] = data['publish-type'];
							item['firstPublishDate'] = item['firstPublishDate'] || item['modified'];
							return item;
						});
						this.load(posts);

						this.postsLoading = false;
						if (typeof cb === 'function')
							cb();
					}.bind(this))
						.e401(function () { })
						.e404(function () { });
				},
				syncOnePost: function (cb, id) {
					oldHttp().get('/blog/post/list/all/' + that._id , {postId:id}).done(function(posts) {
						posts.map(function (item) {
							item.blogId = data._id;
							item['publish-type'] = data['publish-type'];
							item['firstPublishDate'] = item['firstPublishDate'] || item['modified'];
							return item;
						});
						this.load(posts);
						
						if (typeof cb === 'function')
							cb();
					}.bind(this))
						.e401(function () { })
						.e404(function () { });
				},
				addDraft: function(post, callback){
					oldHttp().postJson('/blog/post/' + that._id, post).done(function(result){
						post._id = result._id;
						this.push(post);
						let newPost = this.last();
						newPost.blogId = that._id;
						if(typeof callback === 'function'){
							callback(newPost);
						}
					}.bind(this));
				},
				remove: function(post){
					post.remove();
					Collection.prototype.remove.call(this, post);
				},
				behaviours: 'blog'
			});

			if(this._id){
				this.posts.sync();
			}
		},
		App: function(){
			this.collection(Behaviours.applicationsBehaviours.blog.model.Blog, {
			    sync: function (cb, paginate, search) {
					if (!paginate) {
						this.page=0;
						this.lastPage = false;
						this.all = [];
					}
					if (this.blogsLoading || this.lastPage) {
			            return;
			        }
			        this.blogsLoading = true;
					this.lastPage = false;

					if (!search) {
						search = '';
					} 
					
					oldHttp().get('/blog/list/all',{page: this.page, search: search}).done((blogs) => {
						if(blogs.length > 0){
							this.addRange(blogs);
							this.page++;
						}else{
							this.lastPage=true;
						}

					    this.blogsLoading = false;

						this.trigger('sync');
						if(typeof cb === "function"){
							cb();
						}
					});
				},
				syncAll: function (cb) {
					if (this.blogsLoading) {
						return;
					}
					this.blogsLoading = true;

					oldHttp().get('/blog/list/all').done((blogs) => {
						this.load(blogs);
						this.blogsLoading = false;

						this.trigger('sync');
						if(typeof cb === "function"){
							cb();
						}
					});
				},
				remove: function(blog){
					blog.remove();
					Collection.prototype.remove.call(this, blog);
				},
				counterPost: function(blogId, cb){
					oldHttp().get('/blog/counter/' + blogId).done((obj) => {
						if(typeof cb === "function"){
							cb(obj);
						}
					});
				},
				behaviours: 'blog'
			});
		},
		register: function(){
			this.Blog.prototype.toJSON = function(){
				return {
					_id: this._id,
					title: this.title,
					thumbnail: this.thumbnail || '',
					'comment-type': this['comment-type'] || 'IMMEDIATE',
					'publish-type': this['publish-type'] || 'RESTRAINT',
					description: this.description || ''
				}
			}

			this.Blog.prototype.create = function(fn){
				oldHttp().postJson('/blog', this)
					.done(function(newBlog) {
						this._id = newBlog._id;
						if(typeof fn === 'function'){
							fn();
						}
					}.bind(this));
			}

			this.Blog.prototype.saveModifications = function(fn){
				oldHttp().putJson('/blog/' + this._id, this).done(function(){
					if(typeof fn === 'function'){
						fn();
					}
				})
			}

			this.Blog.prototype.save = function(fn){
				if(this._id){
					this.saveModifications(fn);
				}
				else{
					this.create(fn);
				}
			}

            this.Post.prototype.open = function(cb){
                oldHttp().get('/blog/post/' + this.blogId + '/' + this._id, {state: this.state}).done(function(data){
                    this.content = data.content;
                    this.data.content = data.content;
                    this.trigger('change');
                    if(typeof cb === 'function')
                        cb();
                }.bind(this));
            }

			this.Post.prototype.submit = function(callback){
				this.state = 'SUBMITTED';
				oldHttp().putJson('/blog/post/submit/' + this.blogId + '/' + this._id).done(function(){
					if(typeof callback === 'function'){
						callback();
					}
					this.trigger('change');
				}.bind(this));
			}

			this.Post.prototype.publish = function(callback){
				this.state = 'PUBLISHED';
				if(this['publish-type'] === 'IMMEDIATE'){
				    oldHttp().putJson('/blog/post/submit/' + this.blogId + '/' + this._id).done(function(){
				        if (typeof callback === 'function') {
				            callback();
				            this.trigger('change');
				        }
				    });
					return;
				}
				oldHttp().putJson('/blog/post/publish/' + this.blogId + '/' + this._id).done(function(){
					if(typeof callback === 'function'){
						callback();
						this.trigger('change');
					}
				}.bind(this))
				.e401(function(){
					this.submit(callback);
				}.bind(this));
			}

			this.Post.prototype.create = function(callback, blog, state){
				oldHttp().postJson('/blog/post/' + blog._id, {
					content: this.content,
					title: this.title
				})
				.done(function(data){
					let post = new Behaviours.applicationsBehaviours.blog.model.Post(data);
				    blog.posts.push(post);
					post = blog.posts.last();
					post.blogId = blog._id;
					post['publish-type'] = blog['publish-type'];
					if(state !== 'DRAFT'){
						post.publish(callback);
					}
					else{
						if(typeof  callback === 'function'){
							callback();
						}
					}
				}.bind(this));
			}

			this.Post.prototype.saveModifications = function(callback){
				oldHttp().putJson('/blog/post/' + this.blogId + '/' + this._id, {
					content: this.content,
					title: this.title
				}).done(function(){
                    if(typeof  callback === 'function'){
                        callback();
                    }
                });
			}

			this.Post.prototype.save = function(callback, blog, state){
				if(this._id){
					this.saveModifications(callback);
				}
				else{
					this.create(callback, blog, state);
				}
			}

			this.Post.prototype.remove = function(callback){
				oldHttp().delete('/blog/post/' + this.blogId + '/' + this._id)
					.done(function(){
						if(typeof callback === 'function'){
							callback();
						}
					});
			}

			this.Post.prototype.comment = function(comment){
				oldHttp().postJson('/blog/comment/' + this.blogId + '/' + this._id, comment).done(function(){
					this.comments.sync();
				}.bind(this));
			}

			this.Blog.prototype.open = function(success, error){
			    oldHttp().get('/blog/' + this._id)
                    .done(function (blog) {
					    this.owner = blog.author;
					    this.shortenedTitle = blog.title || '';
					    if(this.shortenedTitle.length > 40){
						    this.shortenedTitle = this.shortenedTitle.substr(0, 38) + '...';
					    }
					    this.updateData(blog);
					    if (typeof success === 'function') {
					        success();
					    }
				    }.bind(this))
			        .e404(function () {
			            if (typeof error === 'function') {
			                error()
			            }
			        }.bind(this))
			        .e401(function () {
			            if (typeof error === 'function') {
			                error()
			            }
			        }.bind(this));
			}

			this.Blog.prototype.remove = async function(){
				await http.delete('/blog/' + this._id);
			}

			this.Comment.prototype.toJSON = function(){
				return {
					comment: this.comment
				}
			}

			model.makeModels(this);
			this.app = new this.App();
		}
	}

Behaviours.register('blog', {
	model: blogModel,
	rights: {
		resource: {
			update: {
				right: 'org-entcore-blog-controllers-PostController|create'
			},
			removePost: {
				right: 'org-entcore-blog-controllers-PostController|delete'
			},
			editPost: {
				right: 'org-entcore-blog-controllers-PostController|update'
			},
			createPost: {
				right: 'org-entcore-blog-controllers-PostController|create'
			},
			publishPost: {
				right: 'org-entcore-blog-controllers-PostController|publish'
			},
			share: {
				right: 'org-entcore-blog-controllers-BlogController|shareJson'
			},
			removeBlog: {
				right: 'org-entcore-blog-controllers-BlogController|delete'
			},
			editBlog: {
				right: 'org-entcore-blog-controllers-BlogController|update'
			},
			removeComment: {
				right: 'org-entcore-blog-controllers-BlogController|delete'
			},
			comment: {
			    right: 'org-entcore-blog-controllers-PostController|comment'
			}
		},
		workflow: {
			create: 'org.entcore.blog.controllers.BlogController|create',
			print: 'org.entcore.blog.controllers.BlogController|print'
		}
	},
	loadResources: function(callback){
		oldHttp().get('/blog/linker').done(function(data){
			let posts = [];
			data.forEach(function(blog){
				if(blog.thumbnail){
					blog.thumbnail = blog.thumbnail + '?thumbnail=48x48';
				}
				else{
					blog.thumbnail = '/img/illustrations/blog.png';
				}

				var addedPosts = _.map(blog.fetchPosts, function(post){
					return {
						owner: {
							name: blog.author.username,
							userId: blog.author.userId
						},
						title: post.title + ' [' + blog.title + ']',
						_id: blog._id,
						icon: blog.thumbnail,
						path: '/blog#/view/' + blog._id + '/' + post._id,
					}
				});
				posts = posts.concat(addedPosts);
			})
			this.resources = posts;
			callback(this.resources);
		}.bind(this));
	},
	sniplets: {
		//TODO Managing paging from sniplets !
		articles: {
			title: 'sniplet.title',
			description: 'sniplet.desc',
			controller: {
			    init: function () {
			        this.foundBlog = true;
					this.me = model.me;
					Behaviours.applicationsBehaviours.blog.model.register();
					let blog = new Behaviours.applicationsBehaviours.blog.model.Blog({ _id: this.source._id });
					this.newPost = new Behaviours.applicationsBehaviours.blog.model.Post();
					blog.open(function(){
                        blog.posts.syncAllPosts();
                    }.bind(this), function () {
					    this.foundBlog = false;
					    this.$apply();
					}.bind(this));
					blog.on('posts.sync, change', function () {
					    this.blog = blog;
					    this.blog.behaviours('blog');
					    this.$apply();
					}.bind(this));
				},
				initSource: function(){
					Behaviours.applicationsBehaviours.blog.model.register();
					let app = new Behaviours.applicationsBehaviours.blog.model.App();
					this.blog = new Behaviours.applicationsBehaviours.blog.model.Blog();
					app.blogs.syncAll(function(){
						this.blogs = app.blogs;
					}.bind(this));
				},
                pickBlog: function(blog) {
                    this.setSnipletSource(blog);
                    this.snipletResource.synchronizeRights();
                },
				createBlog: function(){
					console.log('automatic blog creation');
					if(this.snipletResource){
						this.blog.thumbnail = this.snipletResource.icon || '';
						this.blog.title = this.blog.title || 'Les actualités du site ' + this.snipletResource.title;
						this.blog['comment-type'] = 'IMMEDIATE';
						this.blog.description = '';
					}
					this.blog.save(function(){
						//filler post publication
						let post = {
							state: 'SUBMITTED',
							content: '<p>Voici le premier billet publié sur votre site !</p><p>Vous pouvez créer de nouveaux billets (si vous êtes contributeur) en cliquant sur le bouton "Ajouter un billet" ' +
							'ci-dessus, ou en accédant directement à l\'application Blog. Vos visiteurs pourront également suivre vos actualités depuis leur application, ' +
							'et seront notifiés lorsque votre site sera mis à jour.</p><p>La navigation, à gauche des billets, est automatiquement mise à jour lorsque vous ajoutez'+
							' des pages à votre site.</p>',
							title: 'Votre premier billet !'
						}
						this.blog.posts.addDraft(post, function(post){
							post.publish(function(){
								this.setSnipletSource(this.blog);
								//sharing rights copy
								this.snipletResource.synchronizeRights();
							}.bind(this))
						}.bind(this))
					}.bind(this));

				},
				addPost: function(){
					this.newPost.showCreateBlog = false;
					this.newPost.save(function(){
						this.blog.posts.syncAllPosts(function(){
                            this.$apply();
                        }.bind(this));
                        delete(this.newPost._id);
                        this.newPost.content = "";
                        this.newPost.title = "";
					}.bind(this), this.blog);
				},
				removePost: function(post){
					post.remove(function(){
						this.blog.posts.syncAllPosts(function(){
                            this.$apply();
                        }.bind(this))
					}.bind(this))
				},
				addArticle: function(){
					this.editBlog = {};
				},
				saveEdit: function(post){
                    let scope = this;
					post.save(function(){
                        post.publish(function(){
                            scope.apply();
                        })
                    });
					post.edit = false;
				},
				formatDate: function(date){
					return moment(date).format('D/MM/YYYY');
				},
				getReferencedResources: function(source){
					if(source._id){
						return [source._id];
					}
				},
				publish: function(post){
					post.publish(function(){
						this.blog.posts.syncAllPosts(function(){
                            this.$apply();
                        }.bind(this))
					}.bind(this))
				},
                slidePost: function(post){
                    let scope = this;
                    post.open(function(){
                        scope.blog.posts.forEach(function(p){
                            if(post._id === p._id)
                                p.slided = true;
                            else
                                p.slided = false;
                            scope.$apply();
                        })
                    })
                }
			}
		}
	}
})
