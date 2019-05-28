db.blogs.update( {}, {$set:{"visibility":"OWNER", "slug":null, "publicOn":null}}, {upsert:false, multi:true} );
db.blogs.createIndex( {"slug":"text"}, {unique:true} );