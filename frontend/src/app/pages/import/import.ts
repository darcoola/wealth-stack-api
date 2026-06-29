import { Component } from '@angular/core';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-import',
  imports: [CardModule],
  template: `
    <h1 class="page-title">Import statements</h1>
    <p class="page-subtitle">Upload a bank statement file to import operations.</p>
    <p-card>
      <p>A file upload + bank picker (posting to
        <code>POST /api/v1/bank-statements</code>) will appear here.</p>
    </p-card>
  `,
})
export class Import {}
