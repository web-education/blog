package org.entcore.blog;

import org.entcore.blog.controllers.BlogController;
import org.entcore.blog.controllers.PostController;
import org.entcore.blog.security.BlogResourcesProvider;
import org.entcore.blog.services.impl.BlogRepositoryEvents;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.BaseServer;

public class Blog extends BaseServer {

	@Override
	public void start() {
		setResourceProvider(new BlogResourcesProvider());
		super.start();

		EventStoreFactory eventStoreFactory = EventStoreFactory.getFactory();
		eventStoreFactory.setContainer(container);
		eventStoreFactory.setVertx(vertx);

		setRepositoryEvents(new BlogRepositoryEvents());

		addController(new BlogController());
		addController(new PostController());

	}

}
