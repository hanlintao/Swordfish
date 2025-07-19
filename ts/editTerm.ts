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

class EditTerm {

    electron = require('electron');

    glossary: string = '';
    termId: string = '';
    originalTerm: any = null;

    constructor() {
        this.electron.ipcRenderer.send('get-theme');
        this.electron.ipcRenderer.on('set-theme', (event: Electron.IpcRendererEvent, theme: string) => {
            (document.getElementById('theme') as HTMLLinkElement).href = theme;
        });

        document.addEventListener('keydown', (event: KeyboardEvent) => {
            if (event.code === 'Enter' || event.code === 'NumpadEnter') {
                this.updateTerm();
            }
            if (event.code === 'Escape') {
                this.electron.ipcRenderer.send('close-editTerm');
            }
        });

        this.electron.ipcRenderer.send('get-languages');
        this.electron.ipcRenderer.on('set-languages', (event: Electron.IpcRendererEvent, arg: any) => {
            this.setLanguages(arg);
        });

        (document.getElementById('updateTermButton') as HTMLButtonElement).addEventListener('click', () => {
            this.updateTerm();
        });

        (document.getElementById('deleteTermButton') as HTMLButtonElement).addEventListener('click', () => {
            this.deleteTerm();
        });

        this.electron.ipcRenderer.send('get-edit-term-params');
        this.electron.ipcRenderer.on('set-edit-term-params', (event: Electron.IpcRendererEvent, params: any) => {
            this.glossary = params.glossary;
            this.termId = params.termId;
            this.originalTerm = params.term;
            // 延迟执行populateFields，确保语言列表已加载
            setTimeout(() => {
                this.populateFields();
            }, 100);
        });

        setTimeout(() => {
            this.electron.ipcRenderer.send('set-height', { 
                window: 'editTerm', 
                width: document.body.clientWidth, 
                height: document.body.clientHeight 
            });
        }, 200);
    }

    setLanguages(arg: any): void {
        let array: LanguageInterface[] = arg.languages;
        let languageOptions: string = '<option value="none">选择语言</option>';
        for (let lang of array) {
            languageOptions = languageOptions + '<option value="' + lang.code + '">' + lang.description + '</option>';
        }
        
        let srcLangSelect: HTMLSelectElement = document.getElementById('srcLangSelect') as HTMLSelectElement;
        let tgtLangSelect: HTMLSelectElement = document.getElementById('tgtLangSelect') as HTMLSelectElement;
        
        srcLangSelect.innerHTML = languageOptions;
        tgtLangSelect.innerHTML = languageOptions;
    }

    populateFields(): void {
        console.log('填充表单字段，术语数据:', this.originalTerm);
        if (this.originalTerm) {
            // 支持两种字段名格式
            let sourceText = this.originalTerm.source || this.originalTerm.sourceTerm || '';
            let targetText = this.originalTerm.target || this.originalTerm.targetTerm || '';
            let srcLang = this.originalTerm.srcLang || 'zh-CN';
            let tgtLang = this.originalTerm.tgtLang || 'en-US';
            
            console.log('设置源术语:', sourceText);
            console.log('设置目标术语:', targetText);
            console.log('设置源语言:', srcLang);
            console.log('设置目标语言:', tgtLang);
            
            // 设置文本字段
            (document.getElementById('source') as HTMLInputElement).value = sourceText;
            (document.getElementById('target') as HTMLInputElement).value = targetText;
            
            // 设置语言选择器，添加错误处理
            try {
                let srcLangSelect = document.getElementById('srcLangSelect') as HTMLSelectElement;
                let tgtLangSelect = document.getElementById('tgtLangSelect') as HTMLSelectElement;
                
                if (srcLangSelect && srcLangSelect.options.length > 0) {
                    srcLangSelect.value = srcLang;
                    console.log('源语言选择器设置为:', srcLangSelect.value);
                } else {
                    console.log('源语言选择器未就绪，选项数量:', srcLangSelect ? srcLangSelect.options.length : 0);
                }
                
                if (tgtLangSelect && tgtLangSelect.options.length > 0) {
                    tgtLangSelect.value = tgtLang;
                    console.log('目标语言选择器设置为:', tgtLangSelect.value);
                } else {
                    console.log('目标语言选择器未就绪，选项数量:', tgtLangSelect ? tgtLangSelect.options.length : 0);
                }
            } catch (error) {
                console.error('设置语言选择器时出错:', error);
            }
        } else {
            console.log('没有术语数据可填充');
        }
    }

    updateTerm(): void {
        let sourceInput: HTMLInputElement = document.getElementById('source') as HTMLInputElement;
        let targetInput: HTMLInputElement = document.getElementById('target') as HTMLInputElement;
        let srcLangSelect: HTMLSelectElement = document.getElementById('srcLangSelect') as HTMLSelectElement;
        let tgtLangSelect: HTMLSelectElement = document.getElementById('tgtLangSelect') as HTMLSelectElement;

        let sourceTerm: string = sourceInput.value.trim();
        let targetTerm: string = targetInput.value.trim();
        let srcLang: string = srcLangSelect.value;
        let tgtLang: string = tgtLangSelect.value;

        if (sourceTerm === '') {
            this.electron.ipcRenderer.send('show-message', { 
                type: 'warning', 
                message: '请输入源术语', 
                parent: 'editTerm' 
            });
            return;
        }

        if (targetTerm === '') {
            this.electron.ipcRenderer.send('show-message', { 
                type: 'warning', 
                message: '请输入目标术语', 
                parent: 'editTerm' 
            });
            return;
        }

        if (srcLang === 'none') {
            this.electron.ipcRenderer.send('show-message', { 
                type: 'warning', 
                message: '请选择源语言', 
                parent: 'editTerm' 
            });
            return;
        }

        if (tgtLang === 'none') {
            this.electron.ipcRenderer.send('show-message', { 
                type: 'warning', 
                message: '请选择目标语言', 
                parent: 'editTerm' 
            });
            return;
        }

        let params = {
            glossary: this.glossary,
            termId: this.termId,
            sourceTerm: sourceTerm,
            targetTerm: targetTerm,
            srcLang: srcLang,
            tgtLang: tgtLang
        };

        this.electron.ipcRenderer.send('update-term', params);
    }

    deleteTerm(): void {
        if (confirm('确定要删除这个术语吗？此操作不可撤销。')) {
            let params = {
                glossary: this.glossary,
                termId: this.termId
            };
            this.electron.ipcRenderer.send('delete-term', params);
        }
    }
} 