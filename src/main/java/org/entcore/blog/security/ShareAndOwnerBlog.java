package org.entcore.blog.security;

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.http.filter.MongoAppFilter;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.mongodb.MongoDbConf;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ShareAndOwnerBlog implements ResourcesProvider {
    private MongoDbConf conf = MongoDbConf.getInstance();

    public ShareAndOwnerBlog() {
    }

    public void authorize(HttpServerRequest request, Binding binding, UserInfos user, Handler<Boolean> handler) {
        String id = request.params().get(this.conf.getResourceIdLabel());
        if (id != null && !id.trim().isEmpty()) {
            List<DBObject> groups = new ArrayList();
            String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");
            groups.add(QueryBuilder.start("userId").is(user.getUserId()).put(sharedMethod).is(true).get());
            Iterator var8 = user.getGroupsIds().iterator();

            while(var8.hasNext()) {
                String gpId = (String)var8.next();
                groups.add(QueryBuilder.start("groupId").is(gpId).put(sharedMethod).is(true).get());
            }

            QueryBuilder query = QueryBuilder.start("_id").is(id).or(new DBObject[]{QueryBuilder.start("author.userId").is(user.getUserId()).get(), QueryBuilder.start("shared").elemMatch((new QueryBuilder()).or((DBObject[])groups.toArray(new DBObject[groups.size()])).get()).get()});
            MongoAppFilter.executeCountQuery(request, this.conf.getCollection(), MongoQueryBuilder.build(query), 1, handler);
        } else {
            handler.handle(false);
        }

    }
}
