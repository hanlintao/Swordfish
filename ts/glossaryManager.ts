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

interface Term {
    id?: string;
    source: string;
    target: string;
    srcLang: string;
    tgtLang: string;
}

class GlossaryManager {
    electron = require('electron');
    
    private glossaryId: string = '';
    private glossaryName: string = '';
    private terms: Term[] = [];
    private selectedTerm: Term | null = null;
    
    private titleElement: HTMLElement;
    private loadingElement: HTMLElement;
    private termsTable: HTMLElement;
    private termsTableBody: HTMLElement;
    private noTermsElement: HTMLElement;

    constructor() {
        this.titleElement = document.getElementById('title') as HTMLElement;
        this.loadingElement = document.getElementById('loading') as HTMLElement;
        this.termsTable = document.getElementById('termsTable') as HTMLElement;
        this.termsTableBody = document.getElementById('termsTableBody') as HTMLElement;
        this.noTermsElement = document.getElementById('noTerms') as HTMLElement;
        
        this.setupEventListeners();
        
        // 设置主题
        this.electron.ipcRenderer.send('get-theme');
        this.electron.ipcRenderer.on('set-theme', (event: Electron.IpcRendererEvent, theme: string) => {
            (document.getElementById('theme') as HTMLLinkElement).href = theme;
        });
        
        // 接收术语库信息
        this.electron.ipcRenderer.on('set-glossary-info', (event: Electron.IpcRendererEvent, arg: any) => {
            this.glossaryId = arg.glossaryId;
            this.glossaryName = arg.glossaryName;
            this.titleElement.textContent = `术语库管理 - ${this.glossaryName}`;
            this.loadTerms();
        });
        
        // 接收术语数据
        this.electron.ipcRenderer.on('set-glossary-manager-terms', (event: Electron.IpcRendererEvent, terms: Term[]) => {
            this.terms = terms;
            this.displayTerms();
        });
        
        // 监听刷新信号（删除术语后自动刷新）
        this.electron.ipcRenderer.on('refresh-terms', () => {
            console.log('收到刷新信号，重新加载术语');
            this.loadTerms();
        });
        
        // 监听术语操作结果
        this.electron.ipcRenderer.on('term-operation-result', (event: Electron.IpcRendererEvent, result: any) => {
            if (result.success) {
                this.electron.ipcRenderer.send('show-message', { 
                    type: 'info', 
                    message: result.message || '操作成功' 
                });
                this.loadTerms(); // 重新加载术语列表
            } else {
                this.electron.ipcRenderer.send('show-message', { 
                    type: 'error', 
                    message: result.message || '操作失败' 
                });
            }
        });
        
        // 键盘事件
        document.addEventListener('keydown', (event: KeyboardEvent) => {
            if (event.code === 'Escape') {
                this.electron.ipcRenderer.send('close-glossary-manager');
            }
        });
    }
    
    private setupEventListeners(): void {
        // 添加术语按钮
        (document.getElementById('addTermBtn') as HTMLButtonElement).addEventListener('click', () => {
            this.addTerm();
        });
        
        // 刷新按钮
        (document.getElementById('refreshBtn') as HTMLButtonElement).addEventListener('click', () => {
            this.loadTerms();
        });
        
        // 导出按钮
        (document.getElementById('exportBtn') as HTMLButtonElement).addEventListener('click', () => {
            this.exportGlossary();
        });
    }
    
    private loadTerms(): void {
        this.showLoading();
        console.log('加载术语库术语:', this.glossaryId);
        
        // 发送请求获取术语库的所有术语
        this.electron.ipcRenderer.send('get-glossary-manager-terms', {
            glossaryId: this.glossaryId
        });
    }
    
    private showLoading(): void {
        this.loadingElement.style.display = 'block';
        this.termsTable.style.display = 'none';
        this.noTermsElement.style.display = 'none';
    }
    
    private hideLoading(): void {
        this.loadingElement.style.display = 'none';
    }
    
    private displayTerms(): void {
        this.hideLoading();
        
        if (!this.terms || this.terms.length === 0) {
            this.noTermsElement.style.display = 'block';
            this.termsTable.style.display = 'none';
            return;
        }
        
        this.noTermsElement.style.display = 'none';
        this.termsTable.style.display = 'table';
        
        // 清空表格
        this.termsTableBody.innerHTML = '';
        
        // 填充术语数据
        this.terms.forEach((term, index) => {
            const row = document.createElement('tr');
            row.addEventListener('click', () => this.selectTerm(term, row));
            
            // 序号
            const indexCell = document.createElement('td');
            indexCell.textContent = (index + 1).toString();
            row.appendChild(indexCell);
            
            // 源术语
            const sourceCell = document.createElement('td');
            sourceCell.textContent = term.source;
            row.appendChild(sourceCell);
            
            // 源语言
            const srcLangCell = document.createElement('td');
            srcLangCell.textContent = term.srcLang || 'zh-CN';
            row.appendChild(srcLangCell);
            
            // 目标术语
            const targetCell = document.createElement('td');
            targetCell.textContent = term.target;
            row.appendChild(targetCell);
            
            // 目标语言
            const tgtLangCell = document.createElement('td');
            tgtLangCell.textContent = term.tgtLang || 'en-US';
            row.appendChild(tgtLangCell);
            
            // 操作按钮
            const actionCell = document.createElement('td');
            actionCell.className = 'action-buttons';
            
            const editBtn = document.createElement('button');
            editBtn.textContent = '编辑';
            editBtn.className = 'edit-button';
            editBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.editTerm(term);
            });
            
            const deleteBtn = document.createElement('button');
            deleteBtn.textContent = '删除';
            deleteBtn.className = 'delete-button';
            deleteBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.deleteTerm(term);
            });
            
            actionCell.appendChild(editBtn);
            actionCell.appendChild(deleteBtn);
            row.appendChild(actionCell);
            
            this.termsTableBody.appendChild(row);
        });
    }
    
    private selectTerm(term: Term, row: HTMLTableRowElement): void {
        // 清除之前的选择
        const selectedRows = this.termsTableBody.querySelectorAll('tr.selected');
        selectedRows.forEach(r => r.classList.remove('selected'));
        
        // 选择当前行
        row.classList.add('selected');
        this.selectedTerm = term;
    }
    
    private addTerm(): void {
        // 发送消息打开添加术语对话框
        this.electron.ipcRenderer.send('show-add-term-dialog', {
            glossaryId: this.glossaryId
        });
    }
    
    private editTerm(term: Term): void {
        console.log('编辑术语:', term);
        // 发送消息打开编辑术语对话框
        this.electron.ipcRenderer.send('edit-term', {
            glossary: this.glossaryId,
            termId: term.id,
            term: {
                sourceTerm: term.source,
                targetTerm: term.target,
                srcLang: term.srcLang,
                tgtLang: term.tgtLang
            }
        });
    }
    
    private deleteTerm(term: Term): void {
        if (confirm(`确定要删除术语 "${term.source}" -> "${term.target}" 吗？此操作不可撤销。`)) {
            console.log('删除术语:', term);
            this.electron.ipcRenderer.send('delete-term', {
                glossary: this.glossaryId,
                termId: term.id
            });
        }
    }
    
    private exportGlossary(): void {
        this.electron.ipcRenderer.send('export-glossary-manager', {
            glossaryId: this.glossaryId,
            glossaryName: this.glossaryName
        });
    }
} 