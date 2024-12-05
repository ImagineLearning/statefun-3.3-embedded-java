import logging
import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

def lambda_handler(event, context):

  if 'detail' in event:
    event_detail = event['detail']
  else:
    logger.info('Ignoring - missing .detail in : {}'.format(event))
    return
  
  event_name = 'UnknownEventName'
  if 'eventName' in event_detail:
    event_name = event_detail['eventName']

  logger.info('Incoming event {}: {}'.format(event_name, event))

  # Ignore events other than Create or Update,
  if event_name not in ['CreateApplication', 'UpdateApplication']:
    logger.info('Ignoring - eventName={}'.format(event_name))
    return  

  try:
    region = event['region']
    application_name = event_detail['responseElements']['applicationDetail']['applicationName']

    # kinesisanalyticsv2 API reference: https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/kinesisanalyticsv2.html
    client_kda = boto3.client('kinesisanalyticsv2', region_name=region)

    describe_response = client_kda.describe_application(ApplicationName=application_name)
    logger.info(f'describe_application response: {describe_response}')

    # get application status.
    application_status = describe_response['ApplicationDetail']['ApplicationStatus']

    # an application can be started from 'READY' status only.
    if application_status != 'READY':
      logger.info('No-op for Application {} because ApplicationStatus {} is filtered'.format(application_name, application_status))
      return

    # this call doesn't wait for an application to transfer to 'RUNNING' state.
    client_kda.start_application(ApplicationName=application_name)
    logger.info('Started Application: {}'.format(application_name))
  except Exception as err:
    logger.error(err)
