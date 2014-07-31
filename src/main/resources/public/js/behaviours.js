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
					id: blog._id
				};
			});
			callback(this.resources);
		}.bind(this));
	}
});