import { MTEngine, MTMatch, MTUtils } from "mtengines";

export class OllamaTranslator implements MTEngine {
    apiKey: string;
    model: string;
    baseURL: string;
    srcLang: string = '';
    tgtLang: string = '';

    constructor(apiKey: string, model: string, baseURL: string = 'http://localhost:11434') {
        this.apiKey = apiKey;
        this.model = model;
        this.baseURL = baseURL;
    }

    getName() { return 'Ollama API'; }
    getShortName() { return 'Ollama'; }
    getSourceLanguages() { return MTUtils.getLanguages(); }
    getTargetLanguages() { return MTUtils.getLanguages(); }
    setSourceLanguage(lang: string) { this.srcLang = lang; }
    getSourceLanguage() { return this.srcLang; }
    setTargetLanguage(lang: string) { this.tgtLang = lang; }
    getTargetLanguage() { return this.tgtLang; }

    async translate(source: string): Promise<string> {
        let prompt = MTUtils.translatePropmt(source, this.srcLang, this.tgtLang);
        return new Promise((resolve, reject) => {
            fetch(`${this.baseURL}/api/generate`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    model: this.model,
                    prompt: prompt,
                    stream: false
                })
            }).then(async (response) => {
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const data = await response.json();
                let translation = data.response;
                // 清理响应中的多余字符
                if (translation.startsWith('\n\n')) {
                    translation = translation.substring(2);
                }
                while (translation.startsWith('"') && translation.endsWith('"')) {
                    translation = translation.substring(1, translation.length - 1);
                }
                if (source.startsWith('"') && source.endsWith('"')) {
                    translation = '"' + translation + '"';
                }
                resolve(translation);
            }).catch((error) => {
                console.error('Ollama API Error:', error);
                reject(error);
            });
        });
    }

    async getMTMatch(source: any, terms: any): Promise<MTMatch> {
        let prompt = MTUtils.generatePrompt(source, this.srcLang, this.tgtLang, terms);
        return new Promise((resolve, reject) => {
            fetch(`${this.baseURL}/api/generate`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    model: this.model,
                    prompt: prompt,
                    stream: false
                })
            }).then(async (response) => {
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const data = await response.json();
                let translation = data.response.trim();
                
                // 清理响应中的多余字符和格式
                if (translation.startsWith('\n\n')) {
                    translation = translation.substring(2);
                }
                while (translation.startsWith('"') && translation.endsWith('"')) {
                    translation = translation.substring(1, translation.length - 1);
                }
                
                // 移除所有可能的 XML 标签和代码块标记
                translation = translation.replace(/<[^>]*>/g, ''); // 移除所有 XML 标签
                translation = translation.replace(/```xml\s*/g, ''); // 移除 ```xml
                translation = translation.replace(/```\s*/g, ''); // 移除 ```
                translation = translation.replace(/```/g, ''); // 移除剩余的 ```
                
                // 清理多余的空格和换行
                translation = translation.trim();
                
                // 确保翻译内容被正确包装在 target 标签中
                translation = '<target>' + translation + '</target>';
                
                try {
                    let target = MTUtils.toXMLElement(translation);
                    resolve(new MTMatch(source, target, this.getShortName()));
                } catch (error) {
                    console.error('XML parsing error:', error);
                    // 如果仍然失败，使用最简单的包装
                    let cleanTranslation = '<target>' + translation.replace(/<[^>]*>/g, '').trim() + '</target>';
                    let target = MTUtils.toXMLElement(cleanTranslation);
                    resolve(new MTMatch(source, target, this.getShortName()));
                }
            }).catch((error) => {
                console.error('Ollama API Error:', error);
                reject(error);
            });
        });
    }

    handlesTags() { return true; }
    
    async getModels(): Promise<string[]> {
        try {
            const response = await fetch(`${this.baseURL}/api/tags`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            return data.models.map((model: any) => model.name);
        } catch (error) {
            console.error('Failed to fetch Ollama models:', error);
            // 返回默认模型列表作为后备
            return ["llama3.2", "llama3.1", "llama3", "llama2", "mistral", "codellama", "qwen2"];
        }
    }

    fixesMatches() { return false; }
    fixMatch(originalSource: any, matchSource: any, matchTarget: any): Promise<MTMatch> {
        return Promise.reject('Not implemented');
    }
    fixesTags() { return false; }
    fixTags(source: any, target: any): Promise<any> {
        return Promise.reject('Not implemented');
    }
} 