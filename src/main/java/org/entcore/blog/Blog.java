package org.entcore.blog;

import fr.wseduc.mongodb.MongoDb;
import org.entcore.blog.controllers.BlogController;
import org.entcore.blog.controllers.PostController;
import org.entcore.blog.security.BlogResourcesProvider;
import fr.wseduc.webutils.Server;
import fr.wseduc.webutils.http.Binding;
import org.entcore.blog.services.impl.BlogRepositoryEvents;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.BaseServer;
import org.entcore.common.http.filter.ActionFilter;
import fr.wseduc.webutils.request.filter.SecurityHandler;
import org.entcore.common.user.RepositoryHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Blog extends BaseServer {

	@Override
	public void start() {
		setResourceProvider(new BlogResourcesProvider());
		super.start();

		EventStoreFactory eventStoreFactory = EventStoreFactory.getFactory();
		eventStoreFactory.setContainer(container);
		eventStoreFactory.setVertx(vertx);

		final MongoDb mongo = MongoDb.getInstance();
		mongo.init(Server.getEventBus(vertx),
				container.config().getString("mongo-address", "wse.mongodb.persistor"));

		setRepositoryEvents(new BlogRepositoryEvents(config.getBoolean("share-old-groups-to-users", false)));

		addController(new BlogController());
		addController(new PostController());

	}

}
