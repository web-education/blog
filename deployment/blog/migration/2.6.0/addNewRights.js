var bulk = []
db.blogs.find({}).forEach(function(blog){
    var changed = false;
    if(blog.shared){
        var newRight = "org-entcore-blog-controllers-BlogController|updatePublicBlog";
        var managers = ["org-entcore-blog-controllers-BlogController|delete", "org-entcore-blog-controllers-PostController|delete","org-entcore-blog-controllers-BlogController|update"]
        blog.shared.forEach(function(share){
            managers.forEach(function(right){
                if(share[right] && !share[newRight]){
                    share[newRight] = true;
                    changed = true;
                }
            })
        })
    }
    if(changed){
        bulk.push({
            "updateOne": {
                "filter": { "_id": blog._id },
                "update": {
                    "$set": { 'shared': blog.shared }
                }
            }
        })
    }
});
print("bulk write : "+bulk.length);
db.blogs.bulkWrite(bulk)
