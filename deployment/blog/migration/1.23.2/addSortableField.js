db.posts.find({"firstPublishDate" : { "$exists" : true}}, { "_id" : 1, "firstPublishDate" : 1 }).forEach(function(post) {
    db.posts.update({"_id" : post._id}, { $set : { "sorted" : post.firstPublishDate}});
});

db.posts.find({"firstPublishDate" : { "$exists" : false}}, { "_id" : 1, "modified" : 1 }).forEach(function(post) {
    db.posts.update({"_id" : post._id}, { $set : { "sorted" : post.modified}});
});

db.posts.createIndex( { sorted: -1 } );