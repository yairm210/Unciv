package com.unciv.logic.files;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.typedarrays.ArrayBuffer;

public final class WebFileInterop {
    private WebFileInterop() {
    }

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return false;"
                            + "if (window.__uncivTestFileStore && window.__uncivTestFileStore.enabled) return true;"
                            + "if (window.__uncivEnableWebValidation === true) {"
                            + "  window.__uncivTestFileStore = { enabled: true, files: {}, binary: {}, lastName: '', lastBinaryName: '' };"
                            + "  return true;"
                            + "}"
                            + "return false;")
    public static native boolean isTestFileStoreEnabled();

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return '';"
                            + "var store = window.__uncivTestFileStore;"
                            + "if (!store || !store.enabled) return '';"
                            + "return store.lastName || '';")
    public static native String getTestStoreLastName();

    @JSBody(
            params = {"name", "data"},
            script =
                    "if (typeof window === 'undefined') return;"
                            + "var store = window.__uncivTestFileStore;"
                            + "if (!store || !store.enabled) return;"
                            + "store.files = store.files || {};"
                            + "store.files[name] = String(data || '');"
                            + "store.lastName = name;")
    public static native void writeTestStoreText(String name, String data);

    @JSBody(
            params = {"name"},
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "var store = window.__uncivTestFileStore;"
                            + "if (!store || !store.enabled) return null;"
                            + "var data = store.files && store.files[name];"
                            + "if (data === undefined) return null;"
                            + "return String(data || '');")
    public static native String readTestStoreText(String name);

    @JSFunctor
    public interface LoadTextCallback {
        void handle(String data, String name);
    }

    @JSFunctor
    public interface LoadBinaryCallback {
        void handle(ArrayBuffer data, String name);
    }

    @JSFunctor
    public interface SaveCallback {
        void handle(String location);
    }

    @JSFunctor
    public interface ErrorCallback {
        void handle(String message);
    }

    @JSBody(
            params = {"data", "suggestedName", "extensions", "onSuccess", "onError"},
            script =
                    "if (typeof window === 'undefined') { onError('unavailable'); return; }\n"
                            + "var name = suggestedName && suggestedName.length ? suggestedName : 'unciv-save.json';\n"
                            + "function callError(message){\n"
                            + "  if (typeof onError === 'function') { onError(message); return; }\n"
                            + "  if (onError && typeof onError.handle === 'function') { onError.handle(message); return; }\n"
                            + "  if (onError && typeof onError.$invoke === 'function') { onError.$invoke(message); return; }\n"
                            + "}\n"
                            + "function callSuccess(value){\n"
                            + "  if (typeof onSuccess === 'function') { onSuccess(value); return; }\n"
                            + "  if (onSuccess && typeof onSuccess.handle === 'function') { onSuccess.handle(value); return; }\n"
                            + "  if (onSuccess && typeof onSuccess.$invoke === 'function') { onSuccess.$invoke(value); return; }\n"
                            + "  callError('Missing onSuccess callback');\n"
                            + "}\n"
                            + "if (window.__uncivTestFileStore && window.__uncivTestFileStore.enabled) {\n"
                            + "  var store = window.__uncivTestFileStore;\n"
                            + "  store.files = store.files || {};\n"
                            + "  store.files[name] = String(data || '');\n"
                            + "  store.lastName = name;\n"
                            + "  callSuccess(name);\n"
                            + "  return;\n"
                            + "}\n"
                            + "function cancelOrFail(err){\n"
                            + "  if (err && (err.name === 'AbortError' || err.name === 'NotAllowedError')) { callError('CANCELLED'); return; }\n"
                            + "  var msg = (err && (err.message || err.name)) ? (err.message || err.name) : String(err);\n"
                            + "  callError(msg);\n"
                            + "}\n"
                            + "if (window.showSaveFilePicker) {\n"
                            + "  var opts = { suggestedName: name };\n"
                            + "  if (extensions && extensions.length) {\n"
                            + "    var extList = [];\n"
                            + "    for (var i = 0; i < extensions.length; i++) {\n"
                            + "      var ext = extensions[i];\n"
                            + "      if (!ext) continue;\n"
                            + "      if (ext[0] !== '.') ext = '.' + ext;\n"
                            + "      extList.push(ext);\n"
                            + "    }\n"
                            + "    opts.types = [{ description: 'Unciv file', accept: { '*/*': extList } }];\n"
                            + "  }\n"
                            + "  window.showSaveFilePicker(opts)\n"
                            + "    .then(function(handle){\n"
                            + "      return handle.createWritable().then(function(writable){\n"
                            + "        return writable.write(data).then(function(){\n"
                            + "          return writable.close().then(function(){\n"
                            + "            callSuccess(handle.name || name);\n"
                            + "          });\n"
                            + "        });\n"
                            + "      });\n"
                            + "    })\n"
                            + "    .catch(cancelOrFail);\n"
                            + "  return;\n"
                            + "}\n"
                            + "try {\n"
                            + "  var blob = new Blob([data], { type: 'text/plain' });\n"
                            + "  var url = URL.createObjectURL(blob);\n"
                            + "  var a = document.createElement('a');\n"
                            + "  a.href = url;\n"
                            + "  a.download = name;\n"
                            + "  a.style.display = 'none';\n"
                            + "  document.body.appendChild(a);\n"
                            + "  a.click();\n"
                            + "  document.body.removeChild(a);\n"
                            + "  setTimeout(function(){ URL.revokeObjectURL(url); }, 0);\n"
                            + "  callSuccess(name);\n"
                            + "} catch (err) {\n"
                            + "  cancelOrFail(err);\n"
                            + "}\n")
    public static native void saveText(
            String data,
            String suggestedName,
            String[] extensions,
            SaveCallback onSuccess,
            ErrorCallback onError
    );

    @JSBody(
            params = {"extensions", "onSuccess", "onError"},
            script =
                    "if (typeof window === 'undefined') { onError('unavailable'); return; }\n"
                            + "function callError(message){\n"
                            + "  if (typeof onError === 'function') { onError(message); return; }\n"
                            + "  if (onError && typeof onError.handle === 'function') { onError.handle(message); return; }\n"
                            + "  if (onError && typeof onError.$invoke === 'function') { onError.$invoke(message); return; }\n"
                            + "}\n"
                            + "function callSuccess(data, name){\n"
                            + "  if (typeof onSuccess === 'function') { onSuccess(data, name); return; }\n"
                            + "  if (onSuccess && typeof onSuccess.handle === 'function') { onSuccess.handle(data, name); return; }\n"
                            + "  if (onSuccess && typeof onSuccess.$invoke === 'function') { onSuccess.$invoke(data, name); return; }\n"
                            + "  callError('Missing onSuccess callback');\n"
                            + "}\n"
                            + "if (window.__uncivTestFileStore && window.__uncivTestFileStore.enabled) {\n"
                            + "  var store = window.__uncivTestFileStore;\n"
                            + "  var name = store.lastName || '';\n"
                            + "  var data = store.files && store.files[name];\n"
                            + "  if (!name || data === undefined) { callError('EMPTY'); return; }\n"
                            + "  callSuccess(String(data || ''), name);\n"
                            + "  return;\n"
                            + "}\n"
                            + "function cancelOrFail(err){\n"
                            + "  if (err && (err.name === 'AbortError' || err.name === 'NotAllowedError')) { callError('CANCELLED'); return; }\n"
                            + "  var msg = (err && (err.message || err.name)) ? (err.message || err.name) : String(err);\n"
                            + "  callError(msg);\n"
                            + "}\n"
                            + "if (window.showOpenFilePicker) {\n"
                            + "  var opts = {};\n"
                            + "  if (extensions && extensions.length) {\n"
                            + "    var extList = [];\n"
                            + "    for (var i = 0; i < extensions.length; i++) {\n"
                            + "      var ext = extensions[i];\n"
                            + "      if (!ext) continue;\n"
                            + "      if (ext[0] !== '.') ext = '.' + ext;\n"
                            + "      extList.push(ext);\n"
                            + "    }\n"
                            + "    opts.types = [{ description: 'Unciv file', accept: { '*/*': extList } }];\n"
                            + "  }\n"
                            + "  window.showOpenFilePicker(opts)\n"
                            + "    .then(function(handles){\n"
                            + "      if (!handles || !handles.length) { callError('CANCELLED'); return; }\n"
                            + "      return handles[0].getFile().then(function(file){\n"
                            + "        return file.text().then(function(text){\n"
                            + "          callSuccess(text || '', file.name || 'unknown');\n"
                            + "        });\n"
                            + "      });\n"
                            + "    })\n"
                            + "    .catch(cancelOrFail);\n"
                            + "  return;\n"
                            + "}\n"
                            + "var input = document.createElement('input');\n"
                            + "input.type = 'file';\n"
                            + "if (extensions && extensions.length) {\n"
                            + "  var acceptList = [];\n"
                            + "  for (var j = 0; j < extensions.length; j++) {\n"
                            + "    var ext2 = extensions[j];\n"
                            + "    if (!ext2) continue;\n"
                            + "    if (ext2[0] !== '.') ext2 = '.' + ext2;\n"
                            + "    acceptList.push(ext2);\n"
                            + "  }\n"
                            + "  input.accept = acceptList.join(',');\n"
                            + "}\n"
                            + "input.onchange = function(){\n"
                            + "  var file = input.files && input.files[0];\n"
                            + "  if (!file) { callError('CANCELLED'); return; }\n"
                            + "  var reader = new FileReader();\n"
                            + "  reader.onload = function(){ callSuccess(reader.result || '', file.name || 'unknown'); };\n"
                            + "  reader.onerror = function(){ cancelOrFail(reader.error || 'read_error'); };\n"
                            + "  reader.readAsText(file);\n"
                            + "};\n"
                            + "input.click();\n")
    public static native void loadText(
            String[] extensions,
            LoadTextCallback onSuccess,
            ErrorCallback onError
    );

    @JSBody(
            params = {"extensions", "onSuccess", "onError"},
            script =
                    "if (typeof window === 'undefined') { onError('unavailable'); return; }\n"
                            + "function callError(message){\n"
                            + "  if (typeof onError === 'function') { onError(message); return; }\n"
                            + "  if (onError && typeof onError.handle === 'function') { onError.handle(message); return; }\n"
                            + "  if (onError && typeof onError.$invoke === 'function') { onError.$invoke(message); return; }\n"
                            + "}\n"
                            + "function callSuccess(data, name){\n"
                            + "  if (typeof onSuccess === 'function') { onSuccess(data, name); return; }\n"
                            + "  if (onSuccess && typeof onSuccess.handle === 'function') { onSuccess.handle(data, name); return; }\n"
                            + "  if (onSuccess && typeof onSuccess.$invoke === 'function') { onSuccess.$invoke(data, name); return; }\n"
                            + "  callError('Missing onSuccess callback');\n"
                            + "}\n"
                            + "if (window.__uncivTestFileStore && window.__uncivTestFileStore.enabled) {\n"
                            + "  var store = window.__uncivTestFileStore;\n"
                            + "  var name = store.lastBinaryName || store.lastName || '';\n"
                            + "  var data = store.binary && store.binary[name];\n"
                            + "  if (!name || !data) { callError('EMPTY'); return; }\n"
                            + "  callSuccess(data, name);\n"
                            + "  return;\n"
                            + "}\n"
                            + "function cancelOrFail(err){\n"
                            + "  if (err && (err.name === 'AbortError' || err.name === 'NotAllowedError')) { callError('CANCELLED'); return; }\n"
                            + "  var msg = (err && (err.message || err.name)) ? (err.message || err.name) : String(err);\n"
                            + "  callError(msg);\n"
                            + "}\n"
                            + "if (window.showOpenFilePicker) {\n"
                            + "  var opts = {};\n"
                            + "  if (extensions && extensions.length) {\n"
                            + "    var extList = [];\n"
                            + "    for (var i = 0; i < extensions.length; i++) {\n"
                            + "      var ext = extensions[i];\n"
                            + "      if (!ext) continue;\n"
                            + "      if (ext[0] !== '.') ext = '.' + ext;\n"
                            + "      extList.push(ext);\n"
                            + "    }\n"
                            + "    opts.types = [{ description: 'Unciv file', accept: { '*/*': extList } }];\n"
                            + "  }\n"
                            + "  window.showOpenFilePicker(opts)\n"
                            + "    .then(function(handles){\n"
                            + "      if (!handles || !handles.length) { callError('CANCELLED'); return; }\n"
                            + "      return handles[0].getFile().then(function(file){\n"
                            + "        return file.arrayBuffer().then(function(buffer){\n"
                            + "          callSuccess(buffer, file.name || 'unknown');\n"
                            + "        });\n"
                            + "      });\n"
                            + "    })\n"
                            + "    .catch(cancelOrFail);\n"
                            + "  return;\n"
                            + "}\n"
                            + "var input = document.createElement('input');\n"
                            + "input.type = 'file';\n"
                            + "if (extensions && extensions.length) {\n"
                            + "  var acceptList = [];\n"
                            + "  for (var j = 0; j < extensions.length; j++) {\n"
                            + "    var ext2 = extensions[j];\n"
                            + "    if (!ext2) continue;\n"
                            + "    if (ext2[0] !== '.') ext2 = '.' + ext2;\n"
                            + "    acceptList.push(ext2);\n"
                            + "  }\n"
                            + "  input.accept = acceptList.join(',');\n"
                            + "}\n"
                            + "input.onchange = function(){\n"
                            + "  var file = input.files && input.files[0];\n"
                            + "  if (!file) { callError('CANCELLED'); return; }\n"
                            + "  var reader = new FileReader();\n"
                            + "  reader.onload = function(){ callSuccess(reader.result, file.name || 'unknown'); };\n"
                            + "  reader.onerror = function(){ cancelOrFail(reader.error || 'read_error'); };\n"
                            + "  reader.readAsArrayBuffer(file);\n"
                            + "};\n"
                            + "input.click();\n")
    public static native void loadBinary(
            String[] extensions,
            LoadBinaryCallback onSuccess,
            ErrorCallback onError
    );
}
