Behaviours.register('blog', {
	loadResources: function(callback){
		http().get('/blog/list/all').done(function(blogs){
			this.resources = _.map(blogs, function(blog){
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
					path: '/blog?blog=' + blog._id,
					_id: blog._id
				};
			});
			callback(this.resources);
		}.bind(this));
	},
	sniplets: {
		articles: {
			init: function(){
				this.blog = {};
				http().get('/blog/post/list/all/' + this.source._id).done(function(data){
					this.posts = data;
					this.$apply('posts');
				}.bind(this));
			},
			initSource: function(){
				this.blog = {};
				Behaviours.applicationsBehaviours.blog.loadResources(function(resources){
					this.blogs = resources;
					this.$apply('blogs');
				}.bind(this));
			},
			createBlog: function(){
				console.log(this.snipletResource);
				if(this.snipletResource){
					this.blog.thumbnail = this.snipletResource.icon || '';
					this.blog.title = 'Les actualités du site ' + this.snipletResource.title;
					this.blog['comment-type'] = 'IMMEDIATE';
					this.blog.description = '';
				}
				console.log(this.blog);
				http().post('/blog', this.blog).done(function(newBlog){
					this.setSnipletSource(newBlog);
					if(this.snipletResource && this.snipletResource.shared){
						this.snipletResource.shared.forEach(function(share){
							var actions = _.reject(_.map(share, function(value, prop){ return prop }), function(item){ return item === 'userId' || item === 'groupId' });
							var id = share.groupId
							if(!id){
								id = share.userId;
							}
							http().put('/blog/share/json/' + id, { actions: actions });
						})
					}
				}.bind(this));
			},
			addArticle: function(){
				this.editBlog = {};
			},
			edit: function(){

			},
			formatDate: function(date){
				return moment(date).format('D/MM/YYYY');
			}
		}
	}
});