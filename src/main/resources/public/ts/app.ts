import { model, Collection, Behaviours } from 'entcore/entcore';
import { BlogController } from './controller';

(window as any).BlogController = BlogController;

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