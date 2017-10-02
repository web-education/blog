db.posts.find({"state" : "SUBMITTED"}, { "_id" : 1}).forEach(function(post) {
    db.posts.update({"_id" : post._id}, { $set : { "contribution" : true}});
});
