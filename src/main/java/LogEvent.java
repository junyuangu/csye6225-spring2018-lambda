import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;

public class LogEvent implements RequestHandler<SNSEvent, Object> {

    private DynamoDB myDynamoDB;
    private String DYNAMODB_TABLENAME = "csye6225";
    private Regions REGION = Regions.US_EAST_1;
    protected static String token;
    protected static String app_username;
    protected static final String SES_FROM_ADDRESS = "noreply@csye6225-spring2018-guju.me";
    protected static final String EMAIL_SUBJECT = "Forgot password reset link";
    protected static String HTMLBODY;

    private static String TEXTBODY;


    public Object handleRequest(SNSEvent request, Context context) {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());

        //Loggers
        context.getLogger().log("Invocation started: " + timeStamp);
        context.getLogger().log("1: " + (request == null));
        context.getLogger().log("2: " + (request.getRecords().size()));
        context.getLogger().log(request.getRecords().get(0).getSNS().getMessage());
        timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation completed: " + timeStamp);

        context.getLogger().log("step 1");

        //Execution
        app_username = request.getRecords().get(0).getSNS().getMessage();
        context.getLogger().log(app_username);
        token = UUID.randomUUID().toString();
        context.getLogger().log( token );

        this.initDynamoDbClient(context);

        if( (this.myDynamoDB.getTable(DYNAMODB_TABLENAME).getItem( "userId", app_username)) == null ) {

            context.getLogger().log("User's Reset Request does not exist in the dynamo db table. " +
                    "Will create new token and send an email");
            this.myDynamoDB.getTable(DYNAMODB_TABLENAME)
                    .putItem(
                            new PutItemSpec().withItem( new Item()
                                    .withString( "userId", app_username)
                                    .withString( "token", token )
                                    .withInt( "TTL", 1200 ) ) ); //TTL=20 mins

            TEXTBODY = "https://csye6225-spring2018-guju.me/reset?email=" + app_username + "&token=" + token;
            context.getLogger().log( "This is text body: " + TEXTBODY );
            HTMLBODY = "<h2>You have successfully sent an Email using Amazon SES!</h2>"
                    + "<p>Please reset the password using the below link. " +
                    "Link: https://csye6225-spring2018-guju.me/reset?email=" + app_username + "&token=" + token+"</p>";
            context.getLogger().log( "This is HTML body: " + HTMLBODY );

            context.getLogger().log( "step 2" );
            try {
                AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                        .withRegion( REGION ).build();
                context.getLogger().log( "step 3" );
                SendEmailRequest emailRequest = new SendEmailRequest()
                        .withDestination(
                                new Destination().withToAddresses(app_username) )
                        .withMessage( new Message()
                                .withBody( new Body()
                                        .withHtml( new Content()
                                                .withCharset( "UTF-8" ).withData( HTMLBODY ) )
                                        .withText( new Content()
                                                .withCharset( "UTF-8" ).withData( TEXTBODY ) ) )
                                .withSubject( new Content()
                                        .withCharset( "UTF-8" ).withData(EMAIL_SUBJECT) ) )
                        .withSource(SES_FROM_ADDRESS);
                client.sendEmail( emailRequest );
                context.getLogger().log( "step 4" );
                System.out.println( "Email sent!" );
            } catch (Exception ex) {
                System.out.println( "The email was not sent. Error message: "
                        + ex.getMessage() );
                context.getLogger().log( "step 5" );
            }

        }
        else
        {
            context.getLogger().log("User's Reset Request exists in the dynamo db table");
        }

        return null;
    }

    private void initDynamoDbClient(Context context) {
//        String accessKey = System.getenv("accessKey");
//        String secretKey = System.getenv("secretKey");

        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();

        this.myDynamoDB = new DynamoDB(client);
    }


}
