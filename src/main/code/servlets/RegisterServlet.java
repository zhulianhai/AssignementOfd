package servlets;

import jdbc.DBManager;
import model.User1;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.beans.PropertyVetoException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@WebServlet("/reg.do")
public class RegisterServlet extends HttpServlet {

    private static final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    private static final DocumentBuilder builder = initBuilder();
    private static final float INIT_BALANCE = 0.0F;
    private static final String QUERY_USER_BY_ID = "SELECT user_id FROM user1 WHERE user_id=%s";
    private static final String QUERY_CHECK_USER_PASSWORD = "SELECT user_id FROM user1 WHERE user_id=%s AND user_pass='%s'";
    private static final String QUERY_USER_BALANCE = "SELECT user_balance FROM user_balance WHERE user_id=%s";
    private static final byte CODE_ALL_OK = 0;
    private static final byte CODE_USER_EXISTS = 1;
    private static final byte CODE_ERROR = 2;
    private static final byte CODE_USER_ABSENT = 3;
    private static final byte CODE_PASSWORD_INCORRECT = 4;
    private final Lock lock = new ReentrantLock();

    private static DocumentBuilder initBuilder() {
        try {
            return builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        DBManager dbManager = new DBManager();
        String collect = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(collect.getBytes());
        Document resultDocument = null;
        short status = 500;
        byte code = CODE_ERROR;

        try {
            Document documentParsed;
            lock.lock();
            try {
                documentParsed = builder.parse(inputStream);
            } finally {
                lock.unlock();
            }
            documentParsed.normalize();
            NodeList extra = documentParsed.getElementsByTagName("extra");
            String requestType = documentParsed.getElementsByTagName("request-type").item(0).getTextContent();
            User1 user1 = createUserFromRequest(extra);
            boolean userPresence = checkDbPresence(String.format(QUERY_USER_BY_ID, user1.user_id), dbManager);
            boolean isPasswordCorrect = checkDbPresence(String.format(QUERY_CHECK_USER_PASSWORD, user1.user_id, user1.user_pass), dbManager);

            switch (requestType) {
                case "CREATE-AGT":
                    if (!userPresence) {
                        try (Statement statement = dbManager.getStatement()) {
                            String sql3 = String.format("INSERT INTO user1 VALUES (%d,'%s')", user1.user_id, user1.user_pass);
                            String sql4 = String.format("INSERT INTO user_balance VALUES (%d,%.2f)", user1.user_id, INIT_BALANCE);
                            statement.addBatch(sql3);
                            statement.addBatch(sql4);
                            dbManager.getCurrentConnection().setAutoCommit(false);
                            statement.executeBatch();
                            dbManager.getCurrentConnection().commit();
                            dbManager.getCurrentConnection().setAutoCommit(true);
                            resultDocument = generateDocument(CODE_ALL_OK);
                            status = 200;
                        }
                    } else {
                        code = CODE_USER_EXISTS;
                        throw new Exception("User with such id " + user1.user_id + " is already created");
                    }
                    break;
                case "GET-BALANCE":
                    if (userPresence) {
                        if (!isPasswordCorrect) {
                            code = CODE_PASSWORD_INCORRECT;
                            throw new Exception("Incorrect password");
                        }
                        String sql = String.format(QUERY_USER_BALANCE, user1.user_id);
                        PreparedStatement preparedStatement = dbManager.getPreparedStatement(sql);
                        try (ResultSet resultSet = dbManager.getResultSet(preparedStatement)) {
                            if (resultSet.next()) {
                                float balance = resultSet.getFloat(1);
                                resultDocument = generateDocument(CODE_ALL_OK);
                                status = 200;
                                Element root = resultDocument.getDocumentElement();
                                Element result1 = resultDocument.createElement("extra");
                                result1.appendChild(resultDocument.createTextNode(String.valueOf(balance)));
                                result1.setAttribute("name", "balance");
                                root.appendChild(result1);
                            }
                        } finally {
                            try {
                                if (preparedStatement != null) preparedStatement.close();
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        code = CODE_USER_ABSENT;
                        throw new Exception("User " + user1.user_id + " doesn't exist");
                    }
                    break;
                default:
                    code = CODE_ERROR;
                    throw new Exception("Request type unsupported");
            }
        } catch (Exception e) {
            resultDocument = generateDocument(code);
            e.printStackTrace();
        } finally {
            StringWriter writer = generateResponse(resultDocument);
            resp.setStatus(status);
            resp.setContentType("text/xml");
            resp.getWriter().append(writer.toString());
            dbManager.shutDownConnection();
        }
    }

    private boolean checkDbPresence(String sql, DBManager dbManager) throws SQLException, ClassNotFoundException, PropertyVetoException {
        PreparedStatement preparedStatement = dbManager.getPreparedStatement(sql);
        try (ResultSet resultSet = dbManager.getResultSet(preparedStatement)) {
            return resultSet.next();
        } finally {
            try {
                if (preparedStatement != null) preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private StringWriter generateResponse(Document document1) {
        if (document1 == null) return new StringWriter().append("Something went wrong, response is null");
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer;
            transformer = transformerFactory.newTransformer();
            DOMSource domSource = new DOMSource(document1);
            StringWriter writer = new StringWriter();
            StreamResult resultSring = new StreamResult(writer);
            transformer.transform(domSource, resultSring);
            return writer;
        } catch (TransformerException e) {
            e.printStackTrace();
            return new StringWriter();
        }
    }

    private Document generateDocument(byte code) {
        Document document1 = builder.newDocument();
        Element root = document1.createElement("response");
        Element result = document1.createElement("result-code");
        result.appendChild(document1.createTextNode(String.valueOf(code)));
        document1.appendChild(root);
        root.appendChild(result);
        return document1;
    }

    private User1 createUserFromRequest(NodeList extra) {
        Element element0 = (Element) extra.item(0);
        Element element1 = (Element) extra.item(1);
        User1 user1 = new User1();
        if (element0.getAttribute("name").equals("login") && element1.getAttribute("name").equals("password")) {
            user1.user_pass = element1.getTextContent();
            user1.user_id = Integer.valueOf(element0.getTextContent());
        } else if (element1.getAttribute("name").equals("login") && element0.getAttribute("name").equals("password")) {
            user1.user_pass = element0.getTextContent();
            user1.user_id = Integer.valueOf(element1.getTextContent());
        }
        return user1;
    }
}
