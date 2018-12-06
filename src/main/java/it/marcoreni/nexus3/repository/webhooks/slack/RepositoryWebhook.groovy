package it.marcoreni.nexus3.repository.webhooks.slack;

import org.sonatype.nexus.webhooks.Webhook;
import org.sonatype.nexus.webhooks.WebhookConfiguration;
import org.sonatype.nexus.webhooks.WebhookType;

/**
 * Repository {@link Webhook}.
 *
 * @since 3.1
 */
public abstract class RepositoryWebhook
    extends Webhook
{
  public static final WebhookType TYPE = new WebhookType("repository") {};

  public final WebhookType getType() {
    return TYPE;
  }

  /**
   * Additional configuration exposed for {@link RepositoryWebhook}.
   */
  public interface Configuration
      extends WebhookConfiguration
  {
    String getRepository();
  }
}