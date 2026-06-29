import { Component } from '@angular/core';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-dashboard',
  imports: [CardModule],
  template: `
    <h1 class="page-title">Dashboard</h1>
    <p class="page-subtitle">Overview of your finances.</p>
    <p-card>
      <p>Balances, recent activity and summary charts will appear here.</p>
    </p-card>
  `,
})
export class Dashboard {}
