import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { Operation } from '../../core/operation';
import { OperationsService } from '../../core/operations.service';

@Component({
  selector: 'app-operations',
  imports: [
    DatePipe,
    DecimalPipe,
    TableModule,
    TagModule,
    InputTextModule,
    IconFieldModule,
    InputIconModule,
    ButtonModule,
  ],
  templateUrl: './operations.html',
  styleUrl: './operations.scss',
})
export class Operations {
  private readonly service = inject(OperationsService);

  protected readonly operations = signal<Operation[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Net of all loaded operations (credits minus debits). */
  protected readonly total = computed(() =>
    this.operations().reduce((sum, op) => sum + op.amount, 0),
  );

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.getAll().subscribe({
      next: (operations) => {
        this.operations.set(operations);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load operations. Is the backend running?');
        this.loading.set(false);
      },
    });
  }
}
