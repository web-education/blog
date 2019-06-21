import { model, Collection, Behaviours, ng, routes } from 'entcore';
import { blogController } from './controller';
import { blogPublicController } from './controllers/publicBlog';

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
			action: 'viewPostModal'
		})
		.when('/detail/:blogId/:postId', {
			action: 'viewPostInline'
		})
		.when('/print/:blogId', {
			action: 'print'
		})
		.when('/print/:blogId/post/:postId', {
			action: 'print'
		})
		.otherwise({
			redirectTo: '/list-blogs'
		})
});

ng.controllers.push(blogController);
ng.controllers.push(blogPublicController);

console.log("Initializing model");

model.build = async function(){

	await Behaviours.load('blog');
	
	Behaviours.applicationsBehaviours.blog.model.register();

	(window as any).Blog = Behaviours.applicationsBehaviours.blog.model.Blog;
	(window as any).Post = Behaviours.applicationsBehaviours.blog.model.Post;
	(window as any).Comment = Behaviours.applicationsBehaviours.blog.model.Comment;
	(model as any).blogs = Behaviours.applicationsBehaviours.blog.model.app.blogs;

	(model as any).blogs.removeSelection = async function(): Promise<any>{
		for(let blog of this.selection()){
			const index = this.all.indexOf(blog);
			this.all.splice(index, 1);
			await blog.remove();
		}
	}
}