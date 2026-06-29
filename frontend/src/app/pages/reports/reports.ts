import { Component } from '@angular/core';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-reports',
  imports: [CardModule],
  template: `
    <h1 class="page-title">Reports</h1>
    <p class="page-subtitle">Analyze spending and cash flow over time.</p>
    <p-card>
      <p>Charts such as spending by category and monthly cash flow will appear here.</p>
    </p-card>
  `,
})
export class Reports {}
