package neu.csye6225;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
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
    //private String DYNAMODB_TABLENAME = "csye6225";
    private Regions REGION = Regions.US_EAST_1;
    protected static String token;
    protected static String app_username;
    protected static String SES_FROM_ADDRESS; // = "noreply@csye6225-spring2018-guju.me";
    protected static final String EMAIL_SUBJECT = "Forgot password reset link";
    protected static String HTMLBODY;

    private static String TEXTBODY;


    public Object handleRequest(SNSEvent request, Context context) {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());

        //Loggers
        context.getLogger().log( "Invocation started: " + timeStamp );
        context.getLogger().log( "1. Is request NULL : " + (request == null) );
        context.getLogger().log( "2. records size: " + (request.getRecords().size()) );
        context.getLogger().log(request.getRecords().get(0).getSNS().getMessage());
        timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation completed: " + timeStamp);

        context.getLogger().log("---------------------test 1 completion---------------------------");

        //Execution
        app_username = request.getRecords().get(0).getSNS().getMessage();
        context.getLogger().log( "Email Address which requests reset password: " + app_username );
        token = UUID.randomUUID().toString();
        context.getLogger().log( "token: " + token );

        this.initDynamoDbClient(context);

        String DBTableName = System.getenv("DynamoDB_TableName"); // "csye6225";
        context.getLogger().log("DynamoDB table name: " + DBTableName );
        SES_FROM_ADDRESS = System.getenv( "From_EmailAddress" ); //"noreply@csye6225-spring2018-guju.me";

        Table tableIns = this.myDynamoDB.getTable( DBTableName );
        if( tableIns!=null )
            context.getLogger().log( "Get the table %s from DynamoDB" + DBTableName );
        else
            return null;

        if( (tableIns.getItem( "id", app_username)) == null ) {

            context.getLogger().log("User's Reset Request does not exist in the dynamo db table. " +
                    "Will create new token and send an email");
            this.myDynamoDB.getTable(DBTableName)
                    .putItem(
                            new PutItemSpec().withItem( new Item()
                                    .withString( "id", app_username)
                                    .withString( "token", token )
                                    .withInt( "TTL", 1200 ) ) ); //TTL=20 mins

            TEXTBODY = "https://csye6225-spring2018-guju.me/reset?email=" + app_username + "&token=" + token;
            context.getLogger().log( "This is text body: " + TEXTBODY );
            HTMLBODY = "<h3>You have successfully sent an Email using Amazon SES!</h3>"
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
