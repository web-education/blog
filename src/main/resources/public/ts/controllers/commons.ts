export type BlogModel = {
	_id:string;
	posts: PostsModel
	myRights:any;
	slug: string
	enablePublic: boolean
	modified:{$date:number}
	safeSlug: string;
	slugDomain: string
	visibility: "PUBLIC" | "OWNER";
    'publish-type': "RESTRAINT" | "IMMEDIATE";
    toJSON():any;
	open(success:()=>void, err?:()=>void): void;
}
export type CommentModel = {}
export type ComentsModel = {
	sync():void
	all:CommentModel[];
}
export type State = "PUBLISHED"|"DRAFT";
export type PostModel = {
	_id: string
	slided?: boolean
	state: State
	editing?:boolean
	title:string;
	content:string;
	comments: ComentsModel
	publishing?:boolean;
	blogId?: string;
	author:{
		userId:string,username:string
	}
	updateComment(comment:CommentModel):void;
	comment(comment:CommentModel):void;
	republish(success:()=>void):void;
	publish(success:(succeeded:boolean)=>boolean|void,owner?:boolean):void
	saveModifications(success:(state)=>boolean|void):void;
	save(success:(result:boolean)=>boolean|void, blog?:BlogModel, state?:State):void;
	open(success:()=>void, err?:()=>void): void;
	remove(success:()=>void, b?:boolean,search?:string,filters?:any):void;
}
export type PostsModel = {
	all: PostModel[]
	first(): PostModel
	length():number
	where(args:{state?:State}):PostModel[]
	some(cb:(post:PostModel)=>void):boolean;
	forEach(callback:(post:PostModel)=>void):void
	syncOnePost(success:()=>void,id:string):void
	syncPosts(success:()=>void, b?:boolean,search?:string,filters?:any, publicPost?:boolean):void;
	syncAllPosts(success:()=>void, isPublic?:boolean):void
}