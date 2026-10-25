import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FileDropzoneComponent } from './file-dropzone.component';

describe('Área de anexos', () => {
  function enviarArquivo(fixture: ComponentFixture<FileDropzoneComponent>, arquivo: File): void {
    const entrada = fixture.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(entrada, 'files', {
      configurable: true,
      value: { 0: arquivo, length: 1 } as unknown as FileList,
    });
    entrada.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [FileDropzoneComponent] });
  });

  it('aceita PDF e emite o arquivo selecionado', () => {
    const fixture = TestBed.createComponent(FileDropzoneComponent);
    let selecionado: File | null | undefined;
    fixture.componentInstance.arquivoEscolhido.subscribe((arquivo) => selecionado = arquivo);
    const arquivo = new File(['conteúdo'], 'proposta.pdf', { type: 'application/pdf' });
    fixture.detectChanges();

    enviarArquivo(fixture, arquivo);

    expect(selecionado).toBe(arquivo);
    expect(fixture.nativeElement.textContent).toContain('proposta.pdf');
    fixture.destroy();
  });

  it('rejeita extensão diferente de PDF com mensagem acessível', () => {
    const fixture = TestBed.createComponent(FileDropzoneComponent);
    let erro: string | null | undefined;
    fixture.componentInstance.erroAlterado.subscribe((mensagem) => erro = mensagem);
    fixture.detectChanges();

    enviarArquivo(fixture, new File(['conteúdo'], 'proposta.docx'));

    expect(erro).toBe('Formato não suportado. Envie arquivo PDF.');
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Envie arquivo PDF');
    fixture.destroy();
  });

  it('rejeita arquivo maior que 50 MB', () => {
    const fixture = TestBed.createComponent(FileDropzoneComponent);
    let erro: string | null | undefined;
    fixture.componentInstance.erroAlterado.subscribe((mensagem) => erro = mensagem);
    fixture.detectChanges();
    const arquivo = new File(['conteúdo'], 'proposta.pdf', { type: 'application/pdf' });
    Object.defineProperty(arquivo, 'size', { configurable: true, value: 50 * 1024 * 1024 + 1 });

    enviarArquivo(fixture, arquivo);

    expect(erro).toBe('O arquivo passa de 50 MB.');
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('50 MB');
    fixture.destroy();
  });
});
