db.blogs.update( {}, {$set:{"visibility":"OWNER"},$unset:{"slug":"", "publicOn":""}}, {upsert:false, multi:true} );
db.blogs.createIndex( {"slug": 1}, {unique:true, sparse:true } );