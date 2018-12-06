package it.marcoreni.nexus3.repository.webhooks.slack

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

import org.sonatype.goodies.i18n.I18N
import org.sonatype.goodies.i18n.MessageBundle
import org.sonatype.goodies.i18n.MessageBundle.DefaultMessage
import org.sonatype.nexus.capability.CapabilityConfigurationSupport
import org.sonatype.nexus.capability.CapabilityDescriptorSupport
import org.sonatype.nexus.capability.CapabilitySupport
import org.sonatype.nexus.capability.CapabilityType
import org.sonatype.nexus.capability.Condition
import org.sonatype.nexus.capability.Tag
import org.sonatype.nexus.capability.Taggable
import org.sonatype.nexus.formfields.FormField
import org.sonatype.nexus.formfields.ItemselectFormField
import org.sonatype.nexus.formfields.RepositoryCombobox
import org.sonatype.nexus.formfields.UrlFormField
import org.sonatype.nexus.repository.capability.RepositoryConditions
import org.sonatype.nexus.repository.types.GroupType
import org.sonatype.nexus.webhooks.WebhookService
import org.sonatype.nexus.webhooks.WebhookSubscription

import com.google.common.base.Splitter
import groovy.transform.PackageScope
import groovy.transform.ToString

import static org.sonatype.nexus.capability.CapabilityType.capabilityType

/**
 * Capability to manage {@link RepositoryWebhook} configuration.
 *
 * @since 3.1
 */
@Named(RepositoryWebhookCapability.TYPE_ID)
class RepositoryWebhookCapability
    extends CapabilitySupport<Configuration>
    implements Taggable
{
  public static final String TYPE_ID = 'slackwebhook.repository'

  public static final CapabilityType TYPE = capabilityType(TYPE_ID)

  private static interface Messages
      extends MessageBundle
  {
    @DefaultMessage('Slack Webhook: Repository')
    String name()

    @DefaultMessage('Webhook')
    String category()

    @DefaultMessage('Repository')
    String repositoryLabel()

    @DefaultMessage('Repository to discriminate events from')
    String repositoryHelp()

    @DefaultMessage('Event Types')
    String namesLabel()

    @DefaultMessage('Event types which trigger this Webhook')
    String namesHelp()

    @DefaultMessage('Hook URL')
    String urlLabel()

    @DefaultMessage('Send the request to this Hook URL')
    String urlHelp()

    @DefaultMessage('Slack Webhook')
    String description(String names)

    @DefaultMessage('Slack webhook')
    String about()

  }

  @PackageScope
  static final Messages messages = I18N.create(Messages.class)

  @Inject
  WebhookService webhookService

  @Inject
  RepositoryConditions repositoryConditions

  private final List<WebhookSubscription> subscriptions = []

  @Override
  protected Configuration createConfig(final Map<String, String> properties) {
    return new Configuration(properties)
  }

  @Override
  protected String renderDescription() {
    return messages.description(config.names.join(', '))
  }

  @Override
  Condition activationCondition() {
    return conditions().logical().and(
        conditions().capabilities().passivateCapabilityDuringUpdate(),
        repositoryConditions.repositoryExists({config.repository})
    )
  }

  /**
   * Subscribe to each configured webhook.
   */
  @Override
  protected void onActivate(final Configuration config) {
    webhookService.webhooks.findAll {
      it.type == RepositoryWebhook.TYPE && it.name in config.names
    }.each {
      subscriptions << it.subscribe(config)
    }
  }

  /**
   * Cancel each webhook subscription.
   */
  @Override
  protected void onPassivate(final Configuration config) {
    subscriptions.each {it.cancel()}
    subscriptions.clear()
  }

  @Override
  Set<Tag> getTags() {
    return [
        Tag.categoryTag(messages.category()),
        Tag.repositoryTag(config.repository)
    ]
  }

  //
  // Configuration
  //

  private static final String P_REPOSITORY = 'repository'

  private static final String P_NAMES = 'names'

  private static final String P_URL = 'url'

  @ToString(includePackage = false, includeNames = true)
  static class Configuration
      extends CapabilityConfigurationSupport
      implements RepositoryWebhook.Configuration
  {
    String repository

    List<String> names

    URI url

    Configuration(final Map<String, String> properties) {
      repository = properties[P_REPOSITORY]
      names = parseList(properties[P_NAMES])
      url = parseUri(properties[P_URL])
    }

    private static final Splitter LIST_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings()

    private static List<String> parseList(final String value) {
      List<String> result = []
      result.addAll(LIST_SPLITTER.split(value))
      return result
    }

    String getSecret() {
      return null
    }
  }

  //
  // Descriptor
  //

  @Named(RepositoryWebhookCapability.TYPE_ID)
  @Singleton
  static public class Descriptor
      extends CapabilityDescriptorSupport<Configuration>
      implements Taggable
  {
    private final FormField repository

    // TODO: content-selector

    private final FormField names

    private final FormField url

    Descriptor() {
      this.exposed = true
      this.hidden = false

      this.repository = new RepositoryCombobox(
          P_REPOSITORY,
          messages.repositoryLabel(),
          messages.repositoryHelp(),
          FormField.MANDATORY
      ).excludingAnyOfTypes(GroupType.NAME)

      this.names = new ItemselectFormField(
          P_NAMES,
          messages.namesLabel(),
          messages.namesHelp(),
          FormField.MANDATORY
      ).with {
        storeApi = 'coreui_Webhook.listWithTypeRepository'
        buttons = ['add', 'remove']
        fromTitle = 'Available'
        toTitle = 'Selected'
        return it
      }

      this.url = new UrlFormField(
          P_URL,
          messages.urlLabel(),
          messages.urlHelp(),
          FormField.MANDATORY
      )
    }

    @Override
    CapabilityType type() {
      return TYPE
    }

    @Override
    String name() {
      return messages.name()
    }

    @Override
    List<FormField> formFields() {
      return [repository, names, url]
    }

    @Override
    protected Configuration createConfig(final Map<String, String> properties) {
      return new Configuration(properties)
    }

    @Override
    protected String renderAbout() {
      return messages.about()
    }

    @Override
    Set<Tag> getTags() {
      return [Tag.categoryTag(messages.category())]
    }

    @Override
    protected Set<String> uniqueProperties() {
      return [P_REPOSITORY, P_URL] as Set
    }
  }
}