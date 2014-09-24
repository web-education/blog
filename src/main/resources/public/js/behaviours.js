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
				http().get('/blog/post/list/all/' + this.source._id).done(function(data){
					this.posts = data;
					this.$apply('posts');
				}.bind(this));
			},
			initSource: function(){
				Behaviours.applicationsBehaviours.blog.loadResources(function(resources){
					this.blogs = resources;
					this.$apply('blogs');
				}.bind(this));
			},
			createBlog: function(){

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