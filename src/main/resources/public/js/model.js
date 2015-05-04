model.build = function(){
	Behaviours.applicationsBehaviours.blog.model.register();
	window.Blog = Behaviours.applicationsBehaviours.blog.model.Blog;
	window.Post = Behaviours.applicationsBehaviours.blog.model.Post;
	window.Comment = Behaviours.applicationsBehaviours.blog.model.Comment;
	model.blogs = Behaviours.applicationsBehaviours.blog.model.app.blogs;

	model.blogs.removeSelection = function(){
		this.selection().forEach(function(blog){
			blog.remove();
		});

		Collection.prototype.removeSelection.call(this);
	}
};