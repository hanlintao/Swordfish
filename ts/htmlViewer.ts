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

class HtmlViewer {

    electron = require('electron');

    id: number = -1;

    constructor() {
        // 将electron对象暴露给全局window，以便HTML中的JavaScript可以访问
        (window as any).electron = this.electron;
        
        this.electron.ipcRenderer.send('get-theme');
        this.electron.ipcRenderer.on('set-theme', (event: Electron.IpcRendererEvent, theme: string) => {
            (document.getElementById('theme') as HTMLLinkElement).href = theme;
        });
        document.addEventListener('keydown', (event: KeyboardEvent) => {
            if (event.code === 'Escape') {
                this.electron.ipcRenderer.send('close-htmlViewer', this.id );
            }
        });
        this.electron.ipcRenderer.send('get-html-content');
        this.electron.ipcRenderer.on('set-content', (event: Electron.IpcRendererEvent, arg: any) => {
            let container: HTMLDivElement = document.getElementById('content') as HTMLDivElement;
            container.innerHTML = arg;
            container.style.width = document.body.clientWidth + 'px';
            container.style.height = document.body.clientHeight + 'px';
            
            // 确保在HTML内容设置后，electron对象依然可用
            (window as any).electron = this.electron;
            console.log('HTML内容已设置，electron对象已重新暴露:', !!(window as any).electron);
            
            // 手动触发脚本中的初始化函数
            setTimeout(() => {
                console.log('延迟检查DOM和JavaScript状态');
                console.log('按钮数量:', document.querySelectorAll('button').length);
                console.log('window.electron可用:', !!(window as any).electron);
            }, 100);
        });
        this.electron.ipcRenderer.send('get-html-title');
        this.electron.ipcRenderer.on('set-title', (event: Electron.IpcRendererEvent, title: string) => {
            (document.getElementById('title') as HTMLTitleElement).innerText = title;
        });
        this.electron.ipcRenderer.send('get-html-id');
        this.electron.ipcRenderer.on('set-id', (event: Electron.IpcRendererEvent, id: number) => {
            this.id = id;
        });
        window.addEventListener('resize', () => {
            let container: HTMLDivElement = document.getElementById('content') as HTMLDivElement;
            container.style.width = document.body.clientWidth + 'px';
            container.style.height = document.body.clientHeight + 'px';
        });
    }
}
