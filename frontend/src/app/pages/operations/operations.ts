import { Component } from '@angular/core';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-operations',
  imports: [CardModule],
  template: `
    <h1 class="page-title">Operations</h1>
    <p class="page-subtitle">Browse, filter and search your bank transactions.</p>
    <p-card>
      <p>A sortable, filterable transactions table (from
        <code>GET /api/v1/bank-statements</code>) will appear here.</p>
    </p-card>
  `,
})
export class Operations {}
