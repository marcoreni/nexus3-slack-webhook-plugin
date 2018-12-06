package it.marcoreni.nexus3.repository.webhooks.slack

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

import org.sonatype.nexus.audit.InitiatorProvider
import org.sonatype.nexus.common.node.NodeAccess
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.AssetCreatedEvent
import org.sonatype.nexus.repository.storage.AssetDeletedEvent
import org.sonatype.nexus.repository.storage.AssetEvent
import org.sonatype.nexus.repository.storage.AssetUpdatedEvent
import org.sonatype.nexus.webhooks.Webhook
import org.sonatype.nexus.webhooks.WebhookPayload
import org.sonatype.nexus.webhooks.WebhookRequest

import com.google.common.eventbus.AllowConcurrentEvents
import com.google.common.eventbus.Subscribe

/**
 * Repository {@link Asset} {@link Webhook}.
 *
 * @since 3.1
 */
@Named
@Singleton
class RepositoryAssetWebhook
    extends RepositoryWebhook
{
  public static final String NAME = 'asset'

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
  void on(final AssetCreatedEvent event) {
    maybeQueue(event, EventAction.CREATED)
  }

  @Subscribe
  @AllowConcurrentEvents
  void on(final AssetUpdatedEvent event) {
    maybeQueue(event, EventAction.UPDATED)
  }

  @Subscribe
  @AllowConcurrentEvents
  void on(final AssetDeletedEvent event) {
    maybeQueue(event, EventAction.DELETED)
  }

  /**
   * Maybe queue {@link WebhookRequest} for event matching subscriptions.
   */
  private void maybeQueue(final AssetEvent event, final EventAction eventAction) {
    if (event.local) {

      Asset asset = event.asset
      def payload = new RepositoryAssetWebhookPayload(
          nodeId: nodeAccess.getId(),
          timestamp: new Date(),
          initiator: initiatorProvider.get(),
          repositoryName: event.repositoryName,
          action: eventAction
      )

      payload.asset = new RepositoryAssetWebhookPayload.RepositoryAsset(
          id: asset.entityMetadata.id.value,
          format: asset.format(),
          name: asset.name()
      )

      subscriptions.each {
        def configuration = it.configuration as RepositoryWebhook.Configuration
        if (configuration.repository == event.repositoryName) {
          // TODO: discriminate on content-selector
          queue(it, payload)
        }
      }
    }
  }

  static class RepositoryAssetWebhookPayload
      extends WebhookPayload
  {
    String repositoryName

    EventAction action

    RepositoryAsset asset

    static class RepositoryAsset
    {
      String id

      String format

      String name
    }
  }
}