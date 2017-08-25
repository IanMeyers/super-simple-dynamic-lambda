def lambda_handler(event, context):
    if 'Arg1' not in event or 'Arg2' not in event:
        raise ValueError("Missing Argument")
    else:
        return "%s: %s" % (event['Arg1'],event['Arg2'])