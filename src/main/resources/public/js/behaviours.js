console.log('blog behaviours file')

Behaviours.register('blog', {
	model: {
		Post: function(){

		},
		Blog: function(data){
			var that = this;
			if(data && data._id){
				this._id = data._id;
				http().get('/blog/' + data._id).done(function(blog) {
					this.owner = blog.author;
					this.updateData(blog);
				}.bind(this));
			}

			this.collection(Behaviours.applicationsBehaviours.blog.model.Post, {
				sync: function(){
					var all = [];
					http().get('/blog/post/list/all/' + that._id).done(function(posts){
						all = all.concat(posts);
						http().get('/blog/post/list/all/' + that._id, { state: 'DRAFT'}).done(function(posts){
							all = all.concat(posts);
							http().get('/blog/post/list/all/' + that._id, { state: 'SUBMITTED'}).done(function(posts){
								all = all.concat(posts);
								all = all.map(function(item){
									item.blogId = data._id;
									return item;
								});
								this.load(all);
							}.bind(this));
						}.bind(this));
					}.bind(this));
				},
				addDraft: function(post, callback){
					http().post('/blog/post/' + that._id, post).done(function(result){
						post._id = result._id;
						this.push(post);
						var newPost = this.last();
						newPost.blogId = that._id;
						if(typeof callback === 'function'){
							callback(newPost);
						}
					}.bind(this));
				},
				behaviours: 'blog'
			});
		},
		App: function(){
			this.collection(Behaviours.applicationsBehaviours.blog.model.Blog, {
				sync: function(cb){
					http().get('/blog/list/all').done(function(blogs) {
						blogs = blogs.map(function(item){
							if(item.thumbnail){
								item.icon = item.thumbnail + '?thumbnail=48x48';
							}
							else{
								item.icon = '/img/illustrations/blog.png'
							}
							return item;
						});
						this.load(blogs);
						if(typeof cb === "function"){
							cb();
						}
					}.bind(this));
				},
				behaviours: 'blog'
			});
		},
		register: function(){
			this.Blog.prototype.save = function(cb){
				http()
					.post('/blog', {
						title: this.title,
						thumbnail: this.thumbnail,
						'comment-type': this['comment-type'],
						description: this.description
					})
					.done(function(newBlog) {
						this._id = newBlog._id;
						if(typeof cb === 'function'){
							cb();
						}
					}.bind(this));
			};

			this.Post.prototype.submit = function(callback){
				http().put('/blog/post/submit/' + this.blogId + '/' + this._id).done(function(){
					if(typeof callback === 'function'){
						callback();
					}
				}.bind(this));
			};

			this.Post.prototype.publish = function(callback){
				http().put('/blog/post/publish/' + this.blogId + '/' + this._id).done(function(){
					if(typeof callback === 'function'){
						callback();
					}
				}.bind(this))
				.e401(function(){
					this.submit();
				}.bind(this));
			};

			this.Post.prototype.create = function(callback, blog){
				this.blogId = blog._id;
				http().post('/blog/post/' + blog._id, {
					content: this.content,
					title: this.title
				})
				.done(function(data){
					this._id = data._id;
					blog.posts.sync();
					this.publish(callback);
				}.bind(this))
			};

			this.Post.prototype.saveModifications = function(callback){
				http().put('/blog/post/' + this.blogId + '/' + this._id, {
					content: this.content,
					title: this.title
				})
				.done(function(){
					this.publish(callback);
				}.bind(this));
			};

			this.Post.prototype.save = function(callback, blog){
				if(this._id){
					this.saveModifications(callback);
				}
				else{
					this.create(callback, blog);
				}
			};

			this.Post.prototype.remove = function(callback){
				http().delete('/blog/post/' + this.blogId + '/' + this._id)
					.done(function(){
						if(typeof callback === 'function'){
							callback();
						}
					});
			};

			model.makeModels(this);
			this.app = new this.App();
		}
	},
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
			}
		},
		workflow: {
			create: 'org.entcore.blog.controllers.BlogController|create',
			print: 'org.entcore.blog.controllers.BlogController|print'
		}
	},
	loadResources: function(callback){
		this.model.register();
		this.model.app.blogs.sync(function(){
			this.resources = this.model.app.blogs.map(function(blog){
				if(blog.thumbnail){
					blog.thumbnail = blog.thumbnail + '?thumbnail=48x48';
				}
				else{
					blog.thumbnail = '/img/illustrations/blog.png'
				}
				return {
					title: blog.title,
					owner: {
						name: blog.author.username,
						userId: blog.author.userId
					},
					icon: blog.thumbnail,
					path: '/blog#/view/' + blog._id,
					_id: blog._id
				};
			});
			callback(this.resources);
		}.bind(this));
	},
	sniplets: {
		articles: {
			title: 'sniplet.title',
			description: 'sniplet.desc',
			controller: {
				init: function(){
					this.me = model.me;
					Behaviours.applicationsBehaviours.blog.model.register();
					var blog = new Behaviours.applicationsBehaviours.blog.model.Blog({ _id: this.source._id });
					this.newPost = new Behaviours.applicationsBehaviours.blog.model.Post();
					blog.on('posts.sync, change', function(){
						this.blog = blog;
						this.blog.behaviours('blog');
						this.$apply();
					}.bind(this));
					blog.sync();
				},
				initSource: function(){
					Behaviours.applicationsBehaviours.blog.model.register();
					var app = new Behaviours.applicationsBehaviours.blog.model.App();
					this.blog = new Behaviours.applicationsBehaviours.blog.model.Blog();
					app.blogs.sync(function(){
						this.blogs = app.blogs;
					}.bind(this));
				},
				createBlog: function(){
					console.log('automatic blog creation');
					if(this.snipletResource){
						this.blog.thumbnail = this.snipletResource.icon || '';
						this.blog.title = 'Les actualités du site ' + this.snipletResource.title;
						this.blog['comment-type'] = 'IMMEDIATE';
						this.blog.description = '';
					}
					this.blog.save(function(){
						//filler post publication
						var post = {
							state: 'SUBMITTED',
							content: '<p>Voici le premier article publié sur votre site !</p><p>Vous pouvez créer de nouveaux articles en cliquant sur le bouton "Ajouter un article"' +
							'ci-dessus, ou en accédant directement à l\'application Blog. Vos visiteurs pourront également suivre vos actualités depuis leur application, ' +
							'et seront notifiés lorsque votre site sera mis à jour.</p><p>La navigation, à gauche des articles, est automatiquement mise à jour lorsque vous ajoutez'+
							' des pages à votre site.</p>',
							title: 'Votre premier article !'
						};
						this.blog.posts.addDraft(post, function(post){
							post.publish(function(){
								this.setSnipletSource(this.blog);
								//sharing rights copy
								this.snipletResource.synchronizeRights();
							}.bind(this));
						}.bind(this));
					}.bind(this));

				},
				addPost: function(){
					this.display.showCreateBlog = false;
					this.newPost.save(function(){
						this.blog.posts.sync();
						this.newPost = new Behaviours.applicationsBehaviours.blog.model.Post();
					}.bind(this), this.blog);
				},
				removePost: function(post){
					post.remove(function(){
						this.blog.posts.sync();
					}.bind(this));
				},
				addArticle: function(){
					this.editBlog = {};
				},
				saveEdit: function(post){
					post.save();
					post.edit = false;
				},
				formatDate: function(date){
					return moment(date).format('D/MM/YYYY');
				},
				getReferencedResources: function(source){
					if(source._id){
						return [source._id];
					}
				}
			}
		}
	}
});