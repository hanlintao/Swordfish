import { MTEngine, MTMatch, MTUtils } from "mtengines";
import OpenAI from "openai";

export class DoubaoTranslator implements MTEngine {
    apiKey: string;
    model: string;
    openai: any;
    srcLang: string = '';
    tgtLang: string = '';

    constructor(apiKey: string, model: string) {
        this.apiKey = apiKey;
        this.model = model;
        this.openai = new OpenAI({
            baseURL: 'https://ark.cn-beijing.volces.com/api/v3',
            apiKey: this.apiKey
        });
    }

    getName() { return 'Doubao API'; }
    getShortName() { return 'Doubao'; }
    getSourceLanguages() { return MTUtils.getLanguages(); }
    getTargetLanguages() { return MTUtils.getLanguages(); }
    setSourceLanguage(lang: string) { this.srcLang = lang; }
    getSourceLanguage() { return this.srcLang; }
    setTargetLanguage(lang: string) { this.tgtLang = lang; }
    getTargetLanguage() { return this.tgtLang; }

    translate(source: string): Promise<string> {
        let prompt = MTUtils.translatePropmt(source, this.srcLang, this.tgtLang);
        return new Promise((resolve, reject) => {
            this.openai.chat.completions.create({
                model: this.model,
                messages: [
                    { role: "system", content: MTUtils.getRole(this.srcLang, this.tgtLang) },
                    { role: "user", content: prompt }
                ]
            }).then((completion: any) => {
                let translation = completion.choices[0]?.message?.content || '';
                if (translation.startsWith('\n\n')) translation = translation.substring(2);
                while (translation.startsWith('"') && translation.endsWith('"')) translation = translation.substring(1, translation.length - 1);
                if (source.startsWith('"') && source.endsWith('"')) translation = '"' + translation + '"';
                resolve(translation);
            }).catch((error: any) => {
                console.error('Doubao API Error:', error);
                reject(error);
            });
        });
    }

    getMTMatch(source: any, terms: any): Promise<MTMatch> {
        console.log('[豆包翻译] 收到术语数量:', terms ? terms.length : 0);
        if (terms && terms.length > 0) {
            console.log('[豆包翻译] 术语详情:', JSON.stringify(terms));
        }
        let prompt = MTUtils.generatePrompt(source, this.srcLang, this.tgtLang, terms);
        console.log('[豆包翻译] 生成的提示词:', prompt);
        return new Promise((resolve, reject) => {
            this.openai.chat.completions.create({
                model: this.model,
                messages: [
                    { role: "system", content: MTUtils.getRole(this.srcLang, this.tgtLang) },
                    { role: "user", content: prompt }
                ]
            }).then((completion: any) => {
                let translation = completion.choices[0]?.message?.content?.trim() || '';
                
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
            }).catch((error: any) => {
                console.error('Doubao API Error:', error);
                reject(error);
            });
        });
    }

    handlesTags() { return true; }
    getModels() { return ["your-doubao-model-id"]; }
    fixesMatches() { return false; }
    fixMatch() { return Promise.reject('Not implemented'); }
    fixesTags() { return false; }
    fixTags() { return Promise.reject('Not implemented'); }
} 