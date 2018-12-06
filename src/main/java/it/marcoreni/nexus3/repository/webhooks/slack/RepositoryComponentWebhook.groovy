package it.marcoreni.nexus3.repository.webhooks.slack

import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.sonatype.nexus.webhooks.WebhookConfiguration
import org.sonatype.nexus.webhooks.WebhookSubscription

import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

import org.sonatype.nexus.audit.InitiatorProvider
import org.sonatype.nexus.common.node.NodeAccess
import org.sonatype.nexus.repository.storage.Component
import org.sonatype.nexus.repository.storage.ComponentCreatedEvent
import org.sonatype.nexus.repository.storage.ComponentDeletedEvent
import org.sonatype.nexus.repository.storage.ComponentEvent
import org.sonatype.nexus.repository.storage.ComponentUpdatedEvent
import org.sonatype.nexus.webhooks.Webhook
import org.sonatype.nexus.webhooks.WebhookPayload
import org.sonatype.nexus.webhooks.WebhookRequest

import com.google.common.eventbus.AllowConcurrentEvents
import com.google.common.eventbus.Subscribe

/**
 * Repository {@link Component} {@link Webhook}.
 *
 * @since 3.1
 */
@Named
@Singleton
class RepositoryComponentWebhook
    extends RepositoryWebhook
{
  public static final String NAME = 'component'

  @Inject
  NodeAccess nodeAccess

  @Inject
  InitiatorProvider initiatorProvider

  @Override
  String getName() {
    return NAME
  }

  private static enum EventAction
  {
    CREATED,
    UPDATED,
    DELETED
  }

  @Subscribe
  @AllowConcurrentEvents
  void on(final ComponentCreatedEvent event) {
    maybeQueue(event, EventAction.CREATED)
  }

  @Subscribe
  @AllowConcurrentEvents
  void on(final ComponentUpdatedEvent event) {
    maybeQueue(event, EventAction.UPDATED)
  }

  @Subscribe
  @AllowConcurrentEvents
  void on(final ComponentDeletedEvent event) {
    maybeQueue(event, EventAction.DELETED)
  }

  /**
   * Maybe queue {@link WebhookRequest} for event matching subscriptions.
   */
  private void maybeQueue(final ComponentEvent event, final EventAction eventAction) {
    if (event.local) {

      Component component = event.component
      def payload = new RepositoryComponentWebhookPayload(
          nodeId: nodeAccess.getId(),
          timestamp: new Date(),
          initiator: initiatorProvider.get(),
          repositoryName: event.repositoryName,
          action: eventAction
      )

      payload.component = new RepositoryComponentWebhookPayload.RepositoryComponent(
          id: component.entityMetadata.id.value,
          format: component.format(),
          name: component.name(),
          group: component.group(),
          version: component.version()
      )

      subscriptions.each {
        def configuration = it.configuration as Configuration
        if (configuration.repository == event.repositoryName) {
          // TODO: discriminate on content-selector
          queue(it, payload)
        }
      }
    }
  }

  private CloseableHttpClient httpClient

  @PostConstruct
  void init() {
    httpClient = HttpClientBuilder.create().build()
  }

  protected void queue(WebhookSubscription subscription, RepositoryComponentWebhookPayload body) {
    this.log.debug("Queuing request for {} -> {}", subscription, body)

    String message = "{\"text\": \"Package \"}"

    WebhookConfiguration configuration = subscription.getConfiguration();
    String url = configuration.url
    HttpPost request = new HttpPost(url)

    try {
      request.setEntity(new StringEntity(message))

      HttpResponse response = httpClient.execute(request)

      if (response.getStatusLine().getStatusCode() >= 400
              && response.getStatusLine().getStatusCode() < 600) {
        // either a 4xx or 5xx response from the server, not good
        this.log.warn("Got a bad HTTP response '" + response.getStatusLine() + "' for " + url)
      } else {
        this.log.debug("Response from " + url + " is : " + response.getStatusLine())
      }

    } finally {
      request.releaseConnection();
    }
  }

  static class RepositoryComponentWebhookPayload
      extends WebhookPayload
  {
    String repositoryName

    EventAction action

    RepositoryComponent component

    static class RepositoryComponent
    {
      String id

      String format

      String name

      String group

      String version
    }
  }

}