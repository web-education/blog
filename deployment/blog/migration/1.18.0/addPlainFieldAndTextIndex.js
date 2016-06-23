db.posts.find({"content" : { "$exists" : true}}, { "_id" : 1, "content" : 1 }).forEach(function(post) {
    var content = post.content.replace(/<[^>]*>/g, '');
    db.posts.update({"_id" : post._id}, { $set : { "contentPlain" : content}});
});
db.posts.createIndex({ "title": "text", "contentPlain": "text" });