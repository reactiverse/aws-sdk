def greeter(event, context):
    message = 'Hello {}!'.format(event['name'])
    return {
        'message' : message
    }
