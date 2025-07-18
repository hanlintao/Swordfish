import { MTEngine, MTMatch, MTUtils } from "mtengines";
import OpenAI from "openai";

export class KimiTranslator implements MTEngine {
    apiKey: string;
    model: string;
    openai: any;
    srcLang: string = '';
    tgtLang: string = '';

    constructor(apiKey: string, model: string) {
        this.apiKey = apiKey;
        this.model = model;
        this.openai = new OpenAI({
            baseURL: 'https://api.moonshot.cn/v1',
            apiKey: this.apiKey
        });
    }

    getName() { return 'Kimi API'; }
    getShortName() { return 'Kimi'; }
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
                    { "role": "system", "content": MTUtils.getRole(this.srcLang, this.tgtLang) },
                    { "role": "user", "content": prompt }
                ],
                temperature: 0.3
            }).then((completion: any) => {
                let translation = completion.choices[0].message.content;
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
            }).catch((error: any) => {
                console.error('Kimi API Error:', error);
                reject(error);
            });
        });
    }

    getMTMatch(source: any, terms: any): Promise<MTMatch> {
        console.log('[Kimi翻译] 收到术语数量:', terms ? terms.length : 0);
        if (terms && terms.length > 0) {
            console.log('[Kimi翻译] 术语详情:', JSON.stringify(terms));
        }
        let prompt = MTUtils.generatePrompt(source, this.srcLang, this.tgtLang, terms);
        console.log('[Kimi翻译] 生成的提示词:', prompt);
        return new Promise((resolve, reject) => {
            this.openai.chat.completions.create({
                model: this.model,
                messages: [
                    { "role": "system", "content": MTUtils.getRole(this.srcLang, this.tgtLang) },
                    { "role": "user", "content": prompt }
                ],
                temperature: 0.3
            }).then((completion: any) => {
                let translation = completion.choices[0].message.content.trim();
                // 清理响应中的多余字符
                if (translation.startsWith('\n\n')) {
                    translation = translation.substring(2);
                }
                while (translation.startsWith('"') && translation.endsWith('"')) {
                    translation = translation.substring(1, translation.length - 1);
                }
                if (translation.startsWith('```xml') && translation.endsWith('```')) {
                    translation = translation.substring(6, translation.length - 3).trim();
                }
                if (translation.startsWith('```') && translation.endsWith('```')) {
                    translation = translation.substring(3, translation.length - 3).trim();
                }
                if (!translation.trim().startsWith('<target>') && !translation.trim().endsWith('</target>')) {
                    translation = '<target>' + translation + '</target>';
                }
                let target = MTUtils.toXMLElement(translation);
                resolve(new MTMatch(source, target, this.getShortName()));
            }).catch((error: any) => {
                console.error('Kimi API Error:', error);
                reject(error);
            });
        });
    }

    handlesTags() { return true; }
    getModels() { return ["moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"]; }

    fixesMatches() { return false; }
    fixMatch(originalSource: any, matchSource: any, matchTarget: any): Promise<MTMatch> {
        return Promise.reject('Not implemented');
    }
    fixesTags() { return false; }
    fixTags(source: any, target: any): Promise<any> {
        return Promise.reject('Not implemented');
    }
} 