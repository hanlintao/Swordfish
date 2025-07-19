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

package com.maxprograms.swordfish;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.converters.EncodingResolver;
import com.maxprograms.languages.Language;
import com.maxprograms.swordfish.models.Memory;
import com.maxprograms.swordfish.tbx.Tbx2Tmx;
import com.maxprograms.swordfish.tm.ITmEngine;
import com.maxprograms.swordfish.tm.RemoteDatabase;
import com.maxprograms.swordfish.tm.SqliteDatabase;
import com.maxprograms.xml.Element;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class GlossariesHandler implements HttpHandler {

	private static Logger logger = System.getLogger(GlossariesHandler.class.getName());

	private static Map<String, ITmEngine> engines;
	private static Map<String, Integer> openCount = new Hashtable<>();
	private static Map<String, JSONObject> openTasks = new Hashtable<>();

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		try {
			String request;
			URI uri = exchange.getRequestURI();
			try (InputStream is = exchange.getRequestBody()) {
				request = TmsServer.readRequestBody(is);
			}
			JSONObject response = processRequest(uri.toString(), request);
			byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseHeaders().add("content-type", "application/json; charset=utf-8");
			try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes)) {
				try (OutputStream os = exchange.getResponseBody()) {
					byte[] array = new byte[2048];
					int read;
					while ((read = stream.read(array)) != -1) {
						os.write(array, 0, read);
					}
				}
			}
		} catch (IOException e) {
			MessageFormat mf = new MessageFormat(Messages.getString("GlossariesHandler.0"));
			logger.log(Level.ERROR, mf.format(new String[] { exchange.getRequestURI().toString() }), e);
		}

	}

	private JSONObject processRequest(String url, String request) {
		JSONObject response = new JSONObject();
		try {
			if ("/glossaries/create".equals(url)) {
				response = createGlossary(request);
			} else if ("/glossaries/list".equals(url)) {
				response = listGlossaries();
			} else if ("/glossaries/delete".equals(url)) {
				response = deleteGlossary(request);
			} else if ("/glossaries/export".equals(url)) {
				response = exportGlossary(request);
			} else if ("/glossaries/import".equals(url)) {
				response = importGlossary(request);
			} else if ("/glossaries/status".equals(url)) {
				response = getProcessStatus(request);
			} else if ("/glossaries/search".equals(url)) {
				response = searchTerm(request);
					} else if ("/glossaries/addTerm".equals(url)) {
			response = addTerm(request);
		} else if ("/glossaries/updateTerm".equals(url)) {
			response = updateTerm(request);
		} else if ("/glossaries/deleteTerm".equals(url)) {
			response = deleteTerm(request);
		} else if ("/glossaries/terms".equals(url)) {
			response = getAllTerms(request);
		} else {
				MessageFormat mf = new MessageFormat(Messages.getString("GlossariesHandler.1"));
				response.put(Constants.REASON, mf.format(new String[] { url }));
			}

			if (!response.has(Constants.REASON)) {
				response.put(Constants.STATUS, Constants.SUCCESS);
			} else {
				response.put(Constants.STATUS, Constants.ERROR);
			}
		} catch (Exception j) {
			logger.log(Level.ERROR, j.getMessage(), j);
			response.put(Constants.STATUS, Constants.ERROR);
			response.put(Constants.REASON, j.getMessage());
		}
		return response;
	}

	private static JSONObject getProcessStatus(String request) {
		JSONObject json = new JSONObject(request);
		if (!json.has("process")) {
			JSONObject error = new JSONObject();
			error.put(Constants.REASON, Messages.getString("GlossariesHandler.2"));
			return error;
		}
		String process = json.getString("process");
		if (openTasks.containsKey(process)) {
			return openTasks.get(process);
		}
		JSONObject error = new JSONObject();
		MessageFormat mf = new MessageFormat(Messages.getString("GlossariesHandler.3"));
		error.put(Constants.REASON, mf.format(new String[] { process }));
		return error;
	}

	private static JSONObject createGlossary(String request) throws IOException, SQLException, URISyntaxException {
		JSONObject result = new JSONObject();
		JSONObject json = new JSONObject(request);
		if (!json.has("id")) {
			json.put("id", "" + System.currentTimeMillis());
		}
		if (!json.has("creationDate")) {
			json.put("creationDate", System.currentTimeMillis());
		}
		Memory mem = new Memory(json);
		ITmEngine engine = new SqliteDatabase(mem.getId(), getWorkFolder());
		engine.close();
		Map<String, Memory> glossaries = getGlossaries();
		glossaries.put(mem.getId(), mem);
		ServicesHandler.addClient(json.getString("client"));
		ServicesHandler.addSubject(json.getString("subject"));
		ServicesHandler.addProjectName(json.getString("project"));
		saveGlossariesList(glossaries);
		return result;
	}

	private static Map<String, Memory> getGlossaries() throws IOException {
		Map<String, Memory> glossaries = new Hashtable<>();
		engines = new Hashtable<>();
		File home = new File(getWorkFolder());
		File list = new File(home, "glossaries.json");
		if (!list.exists()) {
			JSONObject json = new JSONObject();
			TmsServer.writeJSON(list, json);
			return glossaries;
		}
		JSONObject json = TmsServer.readJSON(list);
		Set<String> keys = json.keySet();
		Iterator<String> it = keys.iterator();
		while (it.hasNext()) {
			String key = it.next();
			JSONObject obj = json.getJSONObject(key);
			glossaries.put(key, new Memory(obj));
		}
		return glossaries;
	}

	private static synchronized void saveGlossariesList(Map<String, Memory> glossaries) throws IOException {
		JSONObject json = new JSONObject();
		Set<String> keys = glossaries.keySet();
		Iterator<String> it = keys.iterator();
		while (it.hasNext()) {
			String key = it.next();
			Memory m = glossaries.get(key);
			json.put(key, m.toJSON());
		}
		File home = new File(getWorkFolder());
		File list = new File(home, "glossaries.json");
		TmsServer.writeJSON(list, json);
	}

	private static JSONObject listGlossaries() throws IOException {
		JSONObject result = new JSONObject();
		JSONArray array = new JSONArray();
		result.put("glossaries", array);
		Map<String, Memory> glossaries = getGlossaries();
		if (!glossaries.isEmpty()) {
			Vector<Memory> vector = new Vector<>();
			vector.addAll(glossaries.values());
			Collections.sort(vector);
			Iterator<Memory> it = vector.iterator();
			while (it.hasNext()) {
				Memory m = it.next();
				array.put(m.toJSON());
			}
		}
		return result;
	}

	private static JSONObject deleteGlossary(String request) {
		JSONObject result = new JSONObject();
		final JSONObject json = new JSONObject(request);
		if (json.has("glossaries")) {
			final String process = "" + System.currentTimeMillis();
			result.put("process", process);
			JSONObject obj = new JSONObject();
			obj.put(Constants.PROGRESS, Constants.PROCESSING);
			openTasks.put(process, obj);
			new Thread(() -> {
				try {
					Map<String, Memory> glossaries = getGlossaries();
					JSONArray array = json.getJSONArray("glossaries");
					for (int i = 0; i < array.length(); i++) {
						Memory mem = glossaries.get(array.getString(i));
						closeGlossary(mem.getId());
						if (mem.getType().equals(Memory.LOCAL)) {
							deleteGlossaryFolder(mem.getId());
						}
						glossaries.remove(mem.getId());
					}
					saveGlossariesList(glossaries);
					JSONObject completed = new JSONObject();
					completed.put(Constants.PROGRESS, Constants.COMPLETED);
					openTasks.put(process, completed);
				} catch (IOException | SQLException | URISyntaxException e) {
					logger.log(Level.ERROR, e.getMessage(), e);
					JSONObject error = new JSONObject();
					error.put(Constants.REASON, e.getMessage());
					openTasks.put(process, error);
				}
			}).start();
		} else {
			result.put(Constants.REASON, Messages.getString("GlossariesHandler.4"));
		}
		return result;
	}

	private static void deleteGlossaryFolder(String id) {
		try {
			File wfolder = new File(getWorkFolder(), id);
			TmsServer.deleteFolder(wfolder);
		} catch (IOException ioe) {
			MessageFormat mf = new MessageFormat(Messages.getString("GlossariesHandler.5"));
			logger.log(Level.WARNING, mf.format(new String[] { id }));
		}
	}

	private static JSONObject exportGlossary(String request) {
		JSONObject result = new JSONObject();
		final JSONObject json = new JSONObject(request);
		if (!json.has("glossary")) {
			result.put(Constants.REASON, Messages.getString("GlossariesHandler.6"));
			return result;
		}
		if (!json.has("file")) {
			result.put(Constants.REASON, Messages.getString("GlossariesHandler.7"));
			return result;
		}
		if (!json.has("srcLang")) {
			json.put("srcLang", "*all*");
		}
		final String process = "" + System.currentTimeMillis();
		JSONObject obj = new JSONObject();
		obj.put(Constants.PROGRESS, Constants.PROCESSING);
		openTasks.put(process, obj);
		new Thread(() -> {
			try {
				Map<String, Memory> glossaries = getGlossaries();
				Memory mem = glossaries.get(json.getString("glossary"));
				openGlossary(mem);
				ITmEngine engine = getEngine(mem.getId());
				File tmx = new File(json.getString("file"));
				Set<String> langSet = Collections.synchronizedSortedSet(new TreeSet<>());
				if (json.has("languages")) {
					JSONArray langs = json.getJSONArray("languages");
					for (int i = 0; i < langs.length(); i++) {
						langSet.add(langs.getString(i));
					}
				} else {
					langSet = engine.getAllLanguages();
				}
				engine.exportMemory(tmx.getAbsolutePath(), langSet, json.getString("srcLang"));
				closeGlossary(mem.getId());
				JSONObject completed = new JSONObject();
				completed.put(Constants.PROGRESS, Constants.COMPLETED);
				openTasks.put(process, completed);
			} catch (IOException | JSONException | SAXException | ParserConfigurationException | SQLException
					| URISyntaxException e) {
				logger.log(Level.ERROR, e.getMessage(), e);
				JSONObject error = new JSONObject();
				error.put(Constants.REASON, e.getMessage());
				openTasks.put(process, error);
			}
		}).start();
		result.put("process", process);
		return result;
	}

	public static synchronized void openGlossary(String id) throws IOException, SQLException, URISyntaxException {
		Map<String, Memory> glossaries = getGlossaries();
		openGlossary(glossaries.get(id));
	}

	public static synchronized void openGlossary(Memory memory) throws IOException, SQLException, URISyntaxException {
		if (!engines.containsKey(memory.getId())) {
			ITmEngine engine = memory.getType().equals(Memory.LOCAL)
					? new SqliteDatabase(memory.getId(), getWorkFolder())
					: new RemoteDatabase(memory.getServer(), memory.getUser(), memory.getPassword(), memory.getId());
			engines.put(memory.getId(), engine);
			openCount.put(memory.getId(), 0);
		}
		int count = openCount.get(memory.getId());
		openCount.put(memory.getId(), count + 1);
	}

	public static ITmEngine getEngine(String id) throws IOException, SQLException, URISyntaxException {
		if (!engines.containsKey(id)) {
			openGlossary(id);
		}
		return engines.get(id);
	}

	public static synchronized void closeGlossary(String id) throws IOException, SQLException, URISyntaxException {
		if (engines != null && engines.containsKey(id)) {
			int count = openCount.get(id);
			if (count > 1) {
				openCount.put(id, count - 1);
				return;
			}
			engines.get(id).close();
			engines.remove(id);
			openCount.remove(id);
		}
	}

	public static synchronized void closeAll() throws IOException, SQLException, URISyntaxException {
		Set<String> keys = engines.keySet();
		Iterator<String> it = keys.iterator();
		while (it.hasNext()) {
			engines.get(it.next()).close();
		}
		engines.clear();
	}

	private JSONObject importGlossary(String request) {
		JSONObject result = new JSONObject();
		JSONObject json = new JSONObject(request);
		if (!json.has("glossary")) {
			result.put(Constants.REASON, Messages.getString("GlossariesHandler.9"));
			return result;
		}
		String id = json.getString("glossary");

		if (!json.has("file")) {
			result.put(Constants.REASON, Messages.getString("GlossariesHandler.10"));
			return result;
		}
		File glossFile = new File(json.getString("file"));
		if (!glossFile.exists()) {
			result.put(Constants.REASON, Messages.getString("GlossariesHandler.11"));
			return result;
		}
		final String process = "" + System.currentTimeMillis();
		JSONObject obj = new JSONObject();
		obj.put(Constants.PROGRESS, Constants.PROCESSING);
		openTasks.put(process, obj);
		new Thread(() -> {
			try {
				Map<String, Memory> glossaries = getGlossaries();
				openGlossary(glossaries.get(id));
				File tempFile = null;
				String tmxFile = glossFile.getAbsolutePath();
				if (isTBX(glossFile)) {
					tempFile = File.createTempFile("gloss", ".tmx");
					Tbx2Tmx.convert(tmxFile, tempFile.getAbsolutePath());
					tmxFile = tempFile.getAbsolutePath();
				}
				ITmEngine engine = getEngine(id);
				String project = json.has("project") ? json.getString("project") : "";
				String client = json.has("client") ? json.getString("client") : "";
				String subject = json.has("subject") ? json.getString("subject") : "";
				try {
					int imported = engine.storeTMX(tmxFile, project, client, subject);
					MessageFormat mf = new MessageFormat(Messages.getString("GlossariesHandler.12"));
					logger.log(Level.INFO, mf.format(new String[] { "" + imported }));
					JSONObject completed = new JSONObject();
					completed.put("imported", imported);
					completed.put(Constants.PROGRESS, Constants.COMPLETED);
					openTasks.put(process, completed);
				} catch (Exception e) {
					JSONObject error = new JSONObject();
					error.put(Constants.REASON, e.getMessage());
					openTasks.put(process, error);
					logger.log(Level.ERROR, e.getMessage(), e);
				}
				closeGlossary(id);
				if (tempFile != null) {
					Files.delete(tempFile.toPath());
				}
			} catch (IOException | SQLException | SAXException | ParserConfigurationException | URISyntaxException e) {
				logger.log(Level.ERROR, e.getMessage(), e);
				JSONObject error = new JSONObject();
				error.put(Constants.REASON, e.getMessage());
				openTasks.put(process, error);
			}
		}).start();
		result.put("process", process);
		return result;
	}

	public static synchronized JSONArray getGlossariesList() throws IOException {
		JSONArray result = new JSONArray();
		Map<String, Memory> glossaries = getGlossaries();
		if (!glossaries.isEmpty()) {
			Vector<Memory> vector = new Vector<>();
			vector.addAll(glossaries.values());
			Collections.sort(vector);
			Iterator<Memory> it = vector.iterator();
			while (it.hasNext()) {
				Memory m = it.next();
				JSONArray array = new JSONArray();
				array.put(m.getId());
				array.put(m.getName());
				result.put(array);
			}
		}
		return result;
	}

	public static String getWorkFolder() throws IOException {
		File home = TmsServer.getGlossariesFolder();
		if (!home.exists()) {
			Files.createDirectories(home.toPath());
		}
		return home.getAbsolutePath();
	}

	private boolean isTBX(File file) throws IOException {
		byte[] array = new byte[40960];
		try (FileInputStream input = new FileInputStream(file)) {
			if (input.read(array) == -1) {
				throw new IOException(Messages.getString("GlossariesHandler.13"));
			}
		}
		String string = "";
		Charset bom = EncodingResolver.getBOM(file.getAbsolutePath());
		if (bom != null) {
			byte[] efbbbf = { -17, -69, -65 }; // UTF-8
			String utf8 = new String(efbbbf);
			string = new String(array, bom);
			if (string.startsWith("\uFFFE")) {
				string = string.substring("\uFFFE".length());
			} else if (string.startsWith("\uFEFF")) {
				string = string.substring("\uFEFF".length());
			} else if (string.startsWith(utf8)) {
				string = string.substring(utf8.length());
			}
		} else {
			string = new String(array);
		}
		return string.indexOf("<tmx ") == -1;
	}

	private JSONObject addTerm(String request) {
		JSONObject result = new JSONObject();
		JSONObject json = new JSONObject(request);
		if (!json.has("glossary")) {
			result.put(Constants.REASON, Messages.getString("GlossariesHandler.14"));
			return result;
		}
		try {
			String glossary = json.getString("glossary");
			Element tu = new Element("tu");
			Element srcTuv = new Element("tuv");
			srcTuv.setAttribute("xml:lang", json.getString("srcLang"));
			tu.addContent(srcTuv);
			Element srcSeg = new Element("seg");
			srcSeg.setText(json.getString("sourceTerm"));
			srcTuv.addContent(srcSeg);
			Element tgtTuv = new Element("tuv");
			tgtTuv.setAttribute("xml:lang", json.getString("tgtLang"));
			tu.addContent(tgtTuv);
			Element tgtSeg = new Element("seg");
			tgtSeg.setText(json.getString("targetTerm"));
			tgtTuv.addContent(tgtSeg);
			Map<String, Memory> glossaries = getGlossaries();
			openGlossary(glossaries.get(glossary));
			ITmEngine engine = getEngine(glossary);
			engine.storeTu(tu);
			engine.commit();
			closeGlossary(glossary);
		} catch (IOException | SQLException | URISyntaxException e) {
			logger.log(Level.ERROR, e);
			result.put("result", Constants.ERROR);
			result.put(Constants.REASON, e.getMessage());
		}
		return result;
	}

	private JSONObject updateTerm(String request) {
		JSONObject result = new JSONObject();
		JSONObject json = new JSONObject(request);
		if (!json.has("glossary")) {
			result.put(Constants.REASON, Messages.getString("GlossariesHandler.14"));
			return result;
		}
		if (!json.has("termId")) {
			result.put(Constants.REASON, "缺少术语ID参数");
			return result;
		}
		try {
			String glossary = json.getString("glossary");
			String termId = json.getString("termId");
			
			// 先删除原术语
			Map<String, Memory> glossaries = getGlossaries();
			openGlossary(glossaries.get(glossary));
			ITmEngine engine = getEngine(glossary);
			engine.removeTu(termId);
			
			// 添加新术语
			Element tu = new Element("tu");
			tu.setAttribute("id", termId);
			Element srcTuv = new Element("tuv");
			srcTuv.setAttribute("xml:lang", json.getString("srcLang"));
			tu.addContent(srcTuv);
			Element srcSeg = new Element("seg");
			srcSeg.setText(json.getString("sourceTerm"));
			srcTuv.addContent(srcSeg);
			Element tgtTuv = new Element("tuv");
			tgtTuv.setAttribute("xml:lang", json.getString("tgtLang"));
			tu.addContent(tgtTuv);
			Element tgtSeg = new Element("seg");
			tgtSeg.setText(json.getString("targetTerm"));
			tgtTuv.addContent(tgtSeg);
			
			engine.storeTu(tu);
			engine.commit();
			closeGlossary(glossary);
		} catch (IOException | SQLException | URISyntaxException | SAXException | ParserConfigurationException e) {
			logger.log(Level.ERROR, e);
			result.put("result", Constants.ERROR);
			result.put(Constants.REASON, e.getMessage());
		}
		return result;
	}

	private JSONObject deleteTerm(String request) {
		JSONObject result = new JSONObject();
		JSONObject json = new JSONObject(request);
		if (!json.has("glossary")) {
			result.put(Constants.REASON, Messages.getString("GlossariesHandler.14"));
			return result;
		}
		if (!json.has("termId")) {
			result.put(Constants.REASON, "缺少术语ID参数");
			return result;
		}
		try {
			String glossary = json.getString("glossary");
			String termId = json.getString("termId");
			
			Map<String, Memory> glossaries = getGlossaries();
			openGlossary(glossaries.get(glossary));
			ITmEngine engine = getEngine(glossary);
			engine.removeTu(termId);
			engine.commit();
			closeGlossary(glossary);
		} catch (IOException | SQLException | URISyntaxException | SAXException | ParserConfigurationException e) {
			logger.log(Level.ERROR, e);
			result.put("result", Constants.ERROR);
			result.put(Constants.REASON, e.getMessage());
		}
		return result;
	}

	public static JSONObject searchTerm(String request) {
		JSONObject result = new JSONObject();
		JSONObject json = new JSONObject(request);
		if (!json.has("glossary")) {
			result.put(Constants.REASON, Messages.getString("GlossariesHandler.14"));
			return result;
		}
		String searchStr = json.getString("searchStr");
		String srcLang = json.getString("srcLang");
		int similarity = json.getInt("similarity");
		boolean caseSensitive = json.getBoolean("caseSensitive");
		String glossary = json.getString("glossary");
		try {
			List<Element> matches = new Vector<>();
			Map<String, Memory> glossaries = getGlossaries();
			openGlossary(glossaries.get(glossary));
			matches.addAll(getEngine(glossary).searchAll(searchStr, srcLang, similarity, caseSensitive));
			closeGlossary(glossary);
			result.put("count", matches.size());
			result.put("html", generateHTML(matches, glossary));
		} catch (IOException | SAXException | ParserConfigurationException | SQLException | URISyntaxException e) {
			logger.log(Level.ERROR, e);
			result.put("result", Constants.ERROR);
			result.put(Constants.REASON, e.getMessage());
		}
		return result;
	}

	private static String generateHTML(List<Element> matches, String glossary)
			throws IOException, SAXException, ParserConfigurationException {
		StringBuilder builder = new StringBuilder();
		builder.append("<table class='stripes'><tr>");
		List<Language> languages = MemoriesHandler.getLanguages(matches);
		Iterator<Language> st = languages.iterator();
		while (st.hasNext()) {
			builder.append("<th>");
			builder.append(st.next().getDescription());
			builder.append("</th>");
		}
		builder.append("<th>操作</th>");
		builder.append("</tr>");
		for (int i = 0; i < matches.size(); i++) {
			builder.append("<tr>");
			builder.append(parseTU(matches.get(i), languages));
			builder.append("<td class='center'>");
			String termId = matches.get(i).getAttributeValue("id");
			// 如果没有ID，生成一个临时ID
			if (termId == null || termId.isEmpty()) {
				termId = "term_" + System.currentTimeMillis() + "_" + i;
				System.out.println("[generateHTML] 术语没有ID，生成临时ID: " + termId);
			} else {
				System.out.println("[generateHTML] 找到术语ID: " + termId);
			}
			
			// 提取术语的源文本和目标文本
			String sourceText = "";
			String targetText = "";
			String srcLang = "";
			String tgtLang = "";
			
			Element element = matches.get(i);
			List<Element> tuvs = element.getChildren("tuv");
			for (Element tuv : tuvs) {
				String lang = tuv.getAttributeValue("xml:lang");
				Element seg = tuv.getChild("seg");
				if (seg != null) {
					String text = MemoriesHandler.pureText(seg);
					if (lang.startsWith("zh")) {
						sourceText = text;
						srcLang = lang;
					} else if (lang.startsWith("en")) {
						targetText = text;
						tgtLang = lang;
					}
				}
			}
			
			// 转义JavaScript字符串中的特殊字符
			String escapedSource = sourceText.replace("\"", "\\\"").replace("'", "\\'");
			String escapedTarget = targetText.replace("\"", "\\\"").replace("'", "\\'");
			
			builder.append("<button onclick='alert(\"编辑术语: " + termId + "\"); console.log(\"编辑\", \"" + termId + "\"); if(window.electron) { try { window.electron.ipcRenderer.send(\"edit-term\", {glossary: \"" + glossary + "\", termId: \"" + termId + "\", term: {sourceTerm: \"" + escapedSource + "\", targetTerm: \"" + escapedTarget + "\", srcLang: \"" + srcLang + "\", tgtLang: \"" + tgtLang + "\"}}); } catch(e) { alert(\"IPC错误: \" + e.message); } } else { alert(\"electron不可用\"); }' class='smallButton' style='cursor: pointer;'>编辑</button> ");
			builder.append("<button onclick='alert(\"删除术语: " + termId + "\"); if(confirm(\"确定删除?\")) { console.log(\"删除\", \"" + termId + "\"); if(window.electron) { try { window.electron.ipcRenderer.send(\"delete-term\", {glossary: \"" + glossary + "\", termId: \"" + termId + "\"}); } catch(e) { alert(\"删除IPC错误: \" + e.message); } } else { alert(\"electron不可用\"); } }' class='smallButton' style='background-color: #ff4444; cursor: pointer;'>删除</button>");
			builder.append("</td>");
			builder.append("</tr>");
		}
		builder.append("</table>");
		builder.append("<br>");
		builder.append("<script>");
		builder.append("console.log('脚本加载完成');");
		builder.append("window.currentGlossary = '" + glossary + "';");
		builder.append("console.log('设置当前术语库:', window.currentGlossary);");
		builder.append("");
		builder.append("function editTerm(termId) {");
		builder.append("  console.log('=== 编辑术语被点击 ===');");
		builder.append("  console.log('术语ID:', termId);");
		builder.append("  console.log('当前术语库:', window.currentGlossary);");
		builder.append("  console.log('window.electron 存在:', !!window.electron);");
		builder.append("  ");
		builder.append("  alert('编辑按钮被点击！术语ID: ' + termId);");
		builder.append("  ");
		builder.append("  if (window.electron && window.electron.ipcRenderer) {");
		builder.append("    console.log('发送 edit-term IPC 消息');");
		builder.append("    try {");
		builder.append("      window.electron.ipcRenderer.send('edit-term', {");
		builder.append("        glossary: window.currentGlossary,");
		builder.append("        termId: termId");
		builder.append("      });");
		builder.append("      console.log('IPC消息发送成功');");
		builder.append("    } catch (error) {");
		builder.append("      console.error('IPC发送失败:', error);");
		builder.append("      alert('IPC发送失败: ' + error.message);");
		builder.append("    }");
		builder.append("  } else {");
		builder.append("    console.error('electron对象不可用');");
		builder.append("    alert('electron对象不可用，编辑功能无法使用');");
		builder.append("  }");
		builder.append("}");
		builder.append("");
		builder.append("function deleteTerm(termId) {");
		builder.append("  console.log('=== 删除术语被点击 ===');");
		builder.append("  console.log('术语ID:', termId);");
		builder.append("  ");
		builder.append("  if (confirm('确定要删除这个术语吗？此操作不可撤销。')) {");
		builder.append("    alert('确认删除术语: ' + termId);");
		builder.append("    ");
		builder.append("    if (window.electron && window.electron.ipcRenderer) {");
		builder.append("      console.log('发送 delete-term IPC 消息');");
		builder.append("      try {");
		builder.append("        window.electron.ipcRenderer.send('delete-term', {");
		builder.append("          glossary: window.currentGlossary,");
		builder.append("          termId: termId");
		builder.append("        });");
		builder.append("        console.log('删除IPC消息发送成功');");
		builder.append("      } catch (error) {");
		builder.append("        console.error('删除IPC发送失败:', error);");
		builder.append("        alert('删除IPC发送失败: ' + error.message);");
		builder.append("      }");
		builder.append("    } else {");
		builder.append("      console.error('electron对象不可用');");
		builder.append("      alert('electron对象不可用，删除功能无法使用');");
		builder.append("    }");
		builder.append("  }");
		builder.append("}");
		builder.append("");
		builder.append("// 页面加载完成后的测试");
		builder.append("document.addEventListener('DOMContentLoaded', function() {");
		builder.append("  console.log('DOM加载完成');");
		builder.append("  console.log('所有按钮数量:', document.querySelectorAll('button').length);");
		builder.append("});");
		builder.append("");
		builder.append("// 立即测试");
		builder.append("console.log('当前时间:', new Date().toISOString());");
		builder.append("console.log('按钮数量:', document.querySelectorAll ? document.querySelectorAll('button').length : '查询器不可用');");
		builder.append("</script>");
		return builder.toString();
	}

	private static String parseTU(Element element, List<Language> languages)
			throws SAXException, IOException, ParserConfigurationException {
		StringBuilder builder = new StringBuilder();
		Map<String, Element> map = new Hashtable<>();
		List<Element> tuvs = element.getChildren("tuv");
		Iterator<Element> it = tuvs.iterator();
		while (it.hasNext()) {
			Element tuv = it.next();
			map.put(tuv.getAttributeValue("xml:lang"), tuv);
		}
		for (int i = 0; i < languages.size(); i++) {
			Language lang = languages.get(i);
			builder.append("<td ");
			if (lang.isBiDi()) {
				builder.append("dir='rtl'");
			}
			builder.append(" lang='");
			builder.append(lang.getCode());
			builder.append("'>");
			if (map.containsKey(lang.getCode())) {
				Element seg = map.get(lang.getCode()).getChild("seg");
				builder.append(MemoriesHandler.pureText(seg));
			} else {
				builder.append("&nbsp;");
			}
			builder.append("</td>");
		}
		return builder.toString();
	}

	public static String getGlossaryName(String id) throws IOException {
		Map<String, Memory> glossaries = getGlossaries();
		return glossaries.get(id).getName();
	}

	protected static void addGlossary(Memory memory) throws IOException {
		Map<String, Memory> glossaries = getGlossaries();
		glossaries.put(memory.getId(), memory);
		saveGlossariesList(glossaries);
	}

	private static JSONObject getAllTerms(String request) {
		JSONObject result = new JSONObject();
		JSONObject json = new JSONObject(request);
		// 支持两种参数名：glossary 和 glossaryId（术语管理器使用 glossaryId）
		String glossary = null;
		if (json.has("glossary")) {
			glossary = json.getString("glossary");
		} else if (json.has("glossaryId")) {
			glossary = json.getString("glossaryId");
		} else {
			result.put(Constants.REASON, "缺少 glossary 或 glossaryId 参数");
			return result;
		}
		System.out.println("[getAllTerms] 请求glossary: " + glossary);
		try {
			Map<String, Memory> glossaries = getGlossaries();
			System.out.println("[getAllTerms] 找到术语库数量: " + glossaries.size());
			openGlossary(glossaries.get(glossary));
			ITmEngine engine = getEngine(glossary);
			java.util.List<com.maxprograms.xml.Element> terms = engine.getAllTerms();
			System.out.println("[getAllTerms] 从数据库获取术语数量: " + terms.size());
			closeGlossary(glossary);
			JSONArray arr = new JSONArray();
			
			// 使用正确的术语构建模式，参考XliffStore.parseMatches方法
			for (com.maxprograms.xml.Element tu : terms) {
				Map<String, String> langMap = new java.util.Hashtable<>();
				java.util.List<com.maxprograms.xml.Element> tuvs = tu.getChildren("tuv");
				String tuId = tu.getAttributeValue("id");
				System.out.println("[getAllTerms] 处理术语单元，ID: " + tuId + ", tuv数量: " + tuvs.size());
				
				// 收集所有语言变体到Map中
				for (com.maxprograms.xml.Element tuv : tuvs) {
					String lang = tuv.getAttributeValue("xml:lang");
					String seg = "";
					com.maxprograms.xml.Element segElem = tuv.getChild("seg");
					if (segElem != null) {
						seg = com.maxprograms.swordfish.MemoriesHandler.pureText(segElem);
					}
					if (lang != null && !seg.isEmpty()) {
						langMap.put(lang, seg);
						System.out.println("[getAllTerms] 收集语言变体: lang=" + lang + ", seg=" + seg);
					}
				}
				
				// 构建术语对象：中文(zh-CN)作为source，英文(en-US)作为target
				String sourceText = null;
				String targetText = null;
				
				// 优先查找zh-CN，如果没有则查找zh
				if (langMap.containsKey("zh-CN")) {
					sourceText = langMap.get("zh-CN");
				} else if (langMap.containsKey("zh")) {
					sourceText = langMap.get("zh");
				}
				
				// 优先查找en-US，如果没有则查找en
				if (langMap.containsKey("en-US")) {
					targetText = langMap.get("en-US");
				} else if (langMap.containsKey("en")) {
					targetText = langMap.get("en");
				}
				
				// 只有当同时有源语言和目标语言文本时才创建术语
				if (sourceText != null && targetText != null) {
					JSONObject termObj = new JSONObject();
					// 使用前面提取的tuId
					if (tuId != null && !tuId.isEmpty()) {
						termObj.put("id", tuId);
						System.out.println("[getAllTerms] 添加完整术语: id=" + tuId + ", source=" + sourceText + ", target=" + targetText);
					} else {
						System.out.println("[getAllTerms] 警告：术语没有ID: source=" + sourceText + ", target=" + targetText);
					}
					termObj.put("source", sourceText);
					termObj.put("target", targetText);
					termObj.put("srcLang", langMap.containsKey("zh-CN") ? "zh-CN" : "zh");
					termObj.put("tgtLang", langMap.containsKey("en-US") ? "en-US" : "en");
					termObj.put("origin", getGlossaryName(glossary));
					arr.put(termObj);
				} else {
					System.out.println("[getAllTerms] 跳过不完整术语，可用语言: " + langMap.keySet().toString());
				}
			}
			System.out.println("[getAllTerms] 最终术语数组长度: " + arr.length());
			result.put("terms", arr);
		} catch (Exception e) {
			System.out.println("[getAllTerms] 异常: " + e.getMessage());
			e.printStackTrace();
			result.put(Constants.REASON, e.getMessage());
		}
		System.out.println("[getAllTerms] 返回结果: " + result.toString());
		return result;
	}
}
