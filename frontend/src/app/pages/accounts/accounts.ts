import { Component } from '@angular/core';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-accounts',
  imports: [CardModule],
  template: `
    <h1 class="page-title">Accounts</h1>
    <p class="page-subtitle">Give friendly names to your raw account identifiers.</p>
    <p-card>
      <p>Account &rarr; display-name mappings (via
        <code>/api/v1/account-mappings</code>) will be managed here.</p>
    </p-card>
  `,
})
export class Accounts {}
