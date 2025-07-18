/*******************************************************************************
 * Copyright (c) 2007 - 2025 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/

package com.maxprograms.swordfish.tm;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.swordfish.Constants;
import com.maxprograms.swordfish.RemoteUtils;
import com.maxprograms.swordfish.TmsServer;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;

public class RemoteDatabase implements ITmEngine {

    private String server;
    private String dbname;
    private String ticket;
    private SAXBuilder builder;

    public RemoteDatabase(String server, String user, String password, String dbname) throws IOException, URISyntaxException {
        if (server.endsWith("/")) {
            server = server.substring(0, server.length() - 1);
        }
        this.server = server.strip();
        this.dbname = dbname;
        builder = new SAXBuilder();
        ticket = RemoteUtils.getTicket(server, user, password);
        open();
    }

    private JSONObject postMessage(String servlet, JSONObject json) throws IOException, URISyntaxException {
        byte[] bytes = json.toString(2).getBytes(StandardCharsets.UTF_8);
        URL serverUrl = new URI(server + servlet).toURL();
        HttpsURLConnection connection = (HttpsURLConnection) serverUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Session", ticket);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));

        connection.setDoOutput(true);
        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
            outputStream.write(bytes);
            outputStream.flush();
        }

        StringBuilder sb = new StringBuilder();
        try (InputStream stream = connection.getInputStream()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(line);
                }
            }
        }
        JSONObject result = new JSONObject(sb.toString());
        if (result.getString(Constants.STATUS).equals(Constants.OK)) {
            return result;
        }
        throw new IOException(result.getString(Constants.REASON));
    }

    public static String toBase64(String string) {
        return Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getType() {
        return RemoteDatabase.class.getName();
    }

    private void open() throws IOException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "openMemory");
        params.put("memory", dbname);
        postMessage("/memories", params);
    }

    @Override
    public void close() throws IOException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "closeMemory");
        params.put("memory", dbname);
        postMessage("/memories", params);
    }

    @Override
    public String getName() {
        return dbname;
    }

    @Override
    public int storeTMX(String tmxFile, String project, String customer, String subject) throws IOException, URISyntaxException {
        File zipFile = zip(tmxFile);
        String uploaded = upload(zipFile);
        JSONObject params = new JSONObject();
        params.put("command", "importTMX");
        params.put("memory", dbname);
        params.put("file", uploaded);
        params.put("project", project);
        params.put("subject", subject);
        params.put("client", customer);
        params.put("close", false);
        postMessage("/memories", params);
        return -1;
    }

    private File zip(String tmxFile) throws IOException {
        File tmx = new File(tmxFile);
        File zipFile = File.createTempFile("tmx", ".zip", TmsServer.getWorkFolder());
        try (FileOutputStream out = new FileOutputStream(zipFile)) {
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                ZipEntry entry = new ZipEntry(tmx.getName());
                zip.putNextEntry(entry);
                writeEntry(zip, tmx);
                zip.closeEntry();
            }
        }
        return zipFile;
    }

    protected static void writeEntry(ZipOutputStream zip, File file) throws IOException {
        byte[] array = new byte[4096];
        try (FileInputStream in = new FileInputStream(file)) {
            int read = 0;
            while ((read = in.read(array)) != -1) {
                zip.write(array, 0, read);
            }
        }
    }

    private String upload(File zipFile) throws IOException, URISyntaxException {
        URL serverUrl = new URI(server + "/upload").toURL();
        HttpsURLConnection connection = (HttpsURLConnection) serverUrl.openConnection();
        connection.setRequestProperty("Session", ticket);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/zip");
        connection.addRequestProperty("Content-Length", String.valueOf(zipFile.length()));
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
            byte[] array = new byte[4096];
            try (FileInputStream inputStream = new FileInputStream(zipFile)) {
                int read;
                while ((read = inputStream.read(array)) != -1) {
                    outputStream.write(array, 0, read);
                }
                outputStream.flush();
            }
        }
        Files.delete(zipFile.toPath());
        StringBuilder sb = new StringBuilder();
        try (InputStream stream = connection.getInputStream()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(line);
                }
            }
        }
        JSONObject result = new JSONObject(sb.toString());
        if (result.getString(Constants.STATUS).equals(Constants.OK)) {
            return result.getString("file");
        }
        throw new IOException(result.getString(Constants.REASON));
    }

    @Override
    public void exportMemory(String tmxfile, Set<String> langs, String srcLang)
            throws IOException, SAXException, ParserConfigurationException, JSONException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "exportMemory");
        params.put("memory", dbname);
        params.put("srcLang", srcLang);
        if (!langs.isEmpty()) {
            JSONArray array = new JSONArray();
            Iterator<String> it = langs.iterator();
            while (it.hasNext()) {
                array.put(it.next());
            }
            params.put("languages", array);
        }
        JSONObject json = postMessage("/memories", params);
        download(tmxfile, json.getString("file"));
    }

    private void download(String tmxFile, String file) throws IOException, URISyntaxException {
        URL serverUrl = new URI(server + "/download?session=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8)
                + "&file=" + URLEncoder.encode(file, StandardCharsets.UTF_8)).toURL();
        HttpsURLConnection connection = (HttpsURLConnection) serverUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        byte[] bytes = new byte[2048];
        int read = -1;
        try (InputStream input = connection.getInputStream()) {
            try (FileOutputStream out = new FileOutputStream(tmxFile)) {
                while ((read = input.read(bytes)) != -1) {
                    out.write(bytes, 0, read);
                }
            }
        }
    }

    @Override
    public Set<String> getAllClients() throws IOException, URISyntaxException {
        Set<String> result = new TreeSet<>();
        JSONObject params = new JSONObject();
        params.put("command", "memoryClients");
        params.put("memory", dbname);
        JSONObject json = postMessage("/memories", params);
        JSONArray array = json.getJSONArray("clients");
        for (int i = 0; i < array.length(); i++) {
            result.add(array.getString(i));
        }
        return result;
    }

    @Override
    public Set<String> getAllLanguages() throws IOException, URISyntaxException {
        Set<String> result = new TreeSet<>();
        JSONObject params = new JSONObject();
        params.put("command", "memoryLanguages");
        params.put("memory", dbname);
        JSONObject json = postMessage("/memories", params);
        JSONArray array = json.getJSONArray("languages");
        for (int i = 0; i < array.length(); i++) {
            result.add(array.getString(i));
        }
        return result;
    }

    @Override
    public Set<String> getAllProjects() throws IOException, URISyntaxException {
        Set<String> result = new TreeSet<>();
        JSONObject params = new JSONObject();
        params.put("command", "memoryProjects");
        params.put("memory", dbname);
        JSONObject json = postMessage("/memories", params);
        JSONArray array = json.getJSONArray("projects");
        for (int i = 0; i < array.length(); i++) {
            result.add(array.getString(i));
        }
        return result;
    }

    @Override
    public Set<String> getAllSubjects() throws IOException, URISyntaxException {
        Set<String> result = new TreeSet<>();
        JSONObject params = new JSONObject();
        params.put("command", "memorySubjects");
        params.put("memory", dbname);
        JSONObject json = postMessage("/memories", params);
        JSONArray array = json.getJSONArray("subjects");
        for (int i = 0; i < array.length(); i++) {
            result.add(array.getString(i));
        }
        return result;
    }

    @Override
    public List<Match> searchTranslation(String searchStr, String srcLang, String tgtLang, int similarity,
            boolean caseSensitive) throws IOException, SAXException, ParserConfigurationException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "searchTranslation");
        params.put("memory", dbname);
        params.put("searchStr", searchStr);
        params.put("srcLang", srcLang);
        params.put("tgtLang", tgtLang);
        params.put("similarity", similarity);
        params.put("caseSensitive", caseSensitive);
        JSONObject json = postMessage("/memories", params);
        JSONArray array = json.getJSONArray("matches");
        List<Match> matches = new Vector<>();
        for (int i = 0; i < array.length(); i++) {
            matches.add(toMatch(array.getJSONObject(i)));
        }
        return matches;
    }

    @Override
    public List<Element> searchAll(String searchStr, String srcLang, int similarity, boolean caseSensitive)
            throws IOException, SAXException, ParserConfigurationException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "searchAll");
        params.put("memory", dbname);
        params.put("searchStr", searchStr);
        params.put("srcLang", srcLang);
        params.put("similarity", similarity);
        params.put("caseSensitive", caseSensitive);
        JSONObject json = postMessage("/memories", params);
        JSONArray array = json.getJSONArray("tus");
        List<Element> tus = new Vector<>();
        for (int i = 0; i < array.length(); i++) {
            tus.add(toElement(array.getString(i)));
        }
        return tus;
    }

    @Override
    public List<Element> concordanceSearch(String searchStr, String srcLang, int limit, boolean isRegexp,
            boolean caseSensitive) throws IOException, SAXException, ParserConfigurationException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "concordanceSearch");
        params.put("memory", dbname);
        params.put("searchStr", searchStr);
        params.put("srcLang", srcLang);
        params.put("limit", limit);
        params.put("isRegexp", isRegexp);
        params.put("caseSensitive", caseSensitive);
        JSONObject json = postMessage("/memories", params);
        JSONArray array = json.getJSONArray("tus");
        List<Element> tus = new Vector<>();
        for (int i = 0; i < array.length(); i++) {
            tus.add(toElement(array.getString(i)));
        }
        return tus;
    }

    @Override
    public void storeTu(Element tu) throws IOException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "storeTu");
        params.put("memory", dbname);
        params.put("tu", tu.toString());
        postMessage("/memories", params);
    }

    @Override
    public void commit() throws IOException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "commit");
        params.put("memory", dbname);
        postMessage("/memories", params);
    }

    @Override
    public Element getTu(String tuid) throws IOException, SAXException, ParserConfigurationException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "getTu");
        params.put("memory", dbname);
        params.put("tuid", tuid);
        JSONObject json = postMessage("/memories", params);
        return toElement(json.getString("tu"));
    }

    @Override
    public void removeTu(String tuid) throws IOException, SAXException, ParserConfigurationException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "removeTu");
        params.put("memory", dbname);
        params.put("tuid", tuid);
        postMessage("/memories", params);
    }

    @Override
    public void deleteDatabase() throws IOException, URISyntaxException {
        JSONObject params = new JSONObject();
        params.put("command", "removeMemory");
        params.put("memory", dbname);
        postMessage("/memories", params);
    }

    private Match toMatch(JSONObject json) throws SAXException, IOException, ParserConfigurationException {
        Element source = toElement(json.getString("source"));
        Element target = toElement(json.getString("target"));
        int similarity = json.getInt("similarity");
        String origin = json.getString("origin");
        Map<String, String> properties = new HashMap<>();
        if (json.has("properties")) {
            JSONObject props = json.getJSONObject("properties");
            Set<String> keys = props.keySet();
            Iterator<String> it = keys.iterator();
            while (it.hasNext()) {
                String key = it.next();
                properties.put(key, props.getString(key));
            }
        }
        return new Match(source, target, similarity, origin, properties);
    }

    private Element toElement(String string) throws SAXException, IOException, ParserConfigurationException {
        return builder.build(new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8))).getRootElement();
    }

    @Override
    public JSONArray batchTranslate(JSONObject params) throws IOException, URISyntaxException {
        params.put("command", "batchTranslate");
        params.put("memory", dbname);
        JSONObject json = postMessage("/memories", params);
        return json.getJSONArray("matches");
    }

    @Override
    public java.util.List<com.maxprograms.xml.Element> getAllTerms() throws java.sql.SQLException, java.io.IOException, org.xml.sax.SAXException, javax.xml.parsers.ParserConfigurationException {
        // 远程数据库暂不支持获取所有术语，返回空列表
        return new java.util.Vector<>();
    }
}
