import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ChartModule } from 'primeng/chart';
import { DatePickerModule } from 'primeng/datepicker';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { TabsModule } from 'primeng/tabs';
import { CategoryType } from '../../core/category';
import { MonthlyCategoryTotal } from '../../core/report';
import { ReportsService } from '../../core/reports.service';

/** Distinct, high-contrast series colours cycled across months (bars) / categories (lines). */
const PALETTE = [
  '#3b82f6', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6',
  '#ec4899', '#14b8a6', '#f97316', '#6366f1', '#84cc16',
];

const MONTH_NAMES = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

/** One chart per category type, in display order. Uncategorized rows fold into Others. */
const TYPE_ORDER: CategoryType[] = ['SPENDING', 'INCOME', 'OTHERS'];
const TYPE_LABEL: Record<CategoryType, string> = {
  SPENDING: 'Spending',
  INCOME: 'Income',
  OTHERS: 'Others',
};

/** Stable key for a category bucket (real id, or a sentinel for the Uncategorized bucket). */
type CategoryKey = number | 'uncategorized';

interface CategoryBucket {
  key: CategoryKey;
  label: string;
}

/** A titled chart for one category type; `hasData` is false when the type has no categories. */
interface ChartSection {
  type: CategoryType;
  title: string;
  hasData: boolean;
  /** The type's categories, offered as options in the yearly category picker. */
  categories: CategoryBucket[];
  data: unknown;
}

@Component({
  selector: 'app-reports',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    CardModule,
    ChartModule,
    DatePickerModule,
    MultiSelectModule,
    SelectModule,
    TabsModule,
  ],
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class Reports {
  private readonly service = inject(ReportsService);

  protected readonly rows = signal<MonthlyCategoryTotal[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Draws each bar/point's value on the canvas (see [valueLabelsPlugin]). */
  protected readonly plugins = [valueLabelsPlugin];

  // ----- Monthly tab: a set of picked months compared side-by-side as grouped bars. -----
  protected readonly selectedMonthDates = signal<Date[]>([previousMonth()]);

  // ----- Yearly tab: a single year's 12 months as one line per selected category. -----
  protected readonly selectedYear = signal<number | null>(null);
  /** Picked categories per type; defaults to just the first category of each type on load. */
  private readonly selectedYearlyCategories = signal<Map<CategoryType, CategoryKey[]>>(new Map());

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.getCategoryMonthlyTotals().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
        this.applyDefaults();
      },
      error: () => {
        this.error.set('Could not load reports. Is the backend running?');
        this.loading.set(false);
      },
    });
  }

  // ---- Shared derived state ----

  private readonly monthsWithData = computed(() => new Set(this.rows().map((r) => r.month)));

  /** Which chart a row belongs to: its category type, with Uncategorized folded into Others. */
  private group(row: MonthlyCategoryTotal): CategoryType {
    return row.categoryType ?? 'OTHERS';
  }

  private keyOf(row: MonthlyCategoryTotal): CategoryKey {
    return row.categoryId ?? 'uncategorized';
  }

  /** Distinct categories of one type, sorted alphabetically with Uncategorized last. */
  private categoriesForType(type: CategoryType): CategoryBucket[] {
    const byKey = new Map<CategoryKey, CategoryBucket>();
    for (const r of this.rows()) {
      if (this.group(r) !== type) continue;
      const key = this.keyOf(r);
      if (!byKey.has(key)) byKey.set(key, { key, label: r.category ?? 'Uncategorized' });
    }
    return [...byKey.values()].sort((a, b) => {
      if (a.key === 'uncategorized') return 1;
      if (b.key === 'uncategorized') return -1;
      return a.label.localeCompare(b.label);
    });
  }

  protected readonly availableYears = computed(() =>
    [...new Set(this.rows().map((r) => Number(r.month.slice(0, 4))))].sort((a, b) => b - a),
  );

  private defaultsApplied = false;

  /**
   * Seeds selections from the data on first load: the latest year, and (for the Monthly tab) the
   * latest month that actually has operations — so the charts land on real data instead of an empty
   * current month.
   */
  private applyDefaults(): void {
    if (this.defaultsApplied) return;
    this.defaultsApplied = true;
    const years = this.availableYears();
    if (years.length) this.selectedYear.set(years[0]);
    const yearly = new Map<CategoryType, CategoryKey[]>();
    for (const type of TYPE_ORDER) {
      const categories = this.categoriesForType(type);
      if (categories.length) yearly.set(type, [categories[0].key]);
    }
    this.selectedYearlyCategories.set(yearly);
    const months = [...this.monthsWithData()].sort();
    if (months.length) {
      this.selectedMonthDates.set([dateFromMonthKey(months[months.length - 1])]);
    }
  }

  /** Builds one [ChartSection] per category type, in display order. */
  private sections(build: (type: CategoryType, categories: CategoryBucket[]) => unknown): ChartSection[] {
    return TYPE_ORDER.map((type) => {
      const categories = this.categoriesForType(type);
      return {
        type,
        title: TYPE_LABEL[type],
        hasData: categories.length > 0,
        categories,
        data: build(type, categories),
      };
    });
  }

  // ---- Monthly tab ----

  /** Picked months as `YYYY-MM`, ascending. */
  protected readonly selectedMonths = computed(() =>
    this.selectedMonthDates()
      .map(monthKey)
      .sort((a, b) => a.localeCompare(b)),
  );

  /** Picked months that have no operations — surfaced with a prompt to import. */
  protected readonly emptyMonths = computed(() =>
    this.selectedMonths().filter((m) => !this.monthsWithData().has(m)),
  );

  protected readonly monthlySections = computed(() =>
    this.sections((_type, categories) => this.monthlyChartData(categories)),
  );

  /** Grouped bars: categories on the X-axis, one bar (dataset) per selected month. */
  private monthlyChartData(categories: CategoryBucket[]) {
    const months = this.selectedMonths();
    const totals = new Map<string, number>();
    for (const r of this.rows()) {
      totals.set(`${r.month}::${this.keyOf(r)}`, r.total);
    }
    return {
      labels: categories.map((c) => c.label),
      datasets: months.map((m, i) => ({
        label: formatMonth(m),
        backgroundColor: PALETTE[i % PALETTE.length],
        data: categories.map((c) => totals.get(`${m}::${c.key}`) ?? 0),
      })),
    };
  }

  protected readonly barOptions = computed(() => this.chartOptions(true));

  // ---- Yearly tab ----

  protected readonly yearlySections = computed(() =>
    this.sections((type, categories) => this.yearlyChartData(type, categories)),
  );

  /** Selected category keys for a type (read in the template to model the picker). */
  protected yearlyKeysFor(type: CategoryType): CategoryKey[] {
    return this.selectedYearlyCategories().get(type) ?? [];
  }

  /** Replaces the picked categories for a type (immutably, so the computed charts recompute). */
  protected setYearlyKeys(type: CategoryType, keys: CategoryKey[]): void {
    const next = new Map(this.selectedYearlyCategories());
    next.set(type, keys);
    this.selectedYearlyCategories.set(next);
  }

  /**
   * One line per *selected* category across the selected year's 12 months, plus a bar dataset of
   * their monthly sum on a secondary right-hand axis.
   */
  private yearlyChartData(type: CategoryType, categories: CategoryBucket[]) {
    const year = this.selectedYear();
    const selectedKeys = new Set(this.yearlyKeysFor(type));
    const selected = categories.filter((c) => selectedKeys.has(c.key));
    const totals = new Map<string, number>();
    for (const r of this.rows()) {
      totals.set(`${r.month}::${this.keyOf(r)}`, r.total);
    }
    const monthValue = (key: CategoryKey, mi: number) =>
      year == null ? 0 : totals.get(`${year}-${String(mi + 1).padStart(2, '0')}::${key}`) ?? 0;

    const lines = selected.map((c, i) => ({
      type: 'line' as const,
      label: c.label,
      data: MONTH_NAMES.map((_, mi) => monthValue(c.key, mi)),
      borderColor: PALETTE[i % PALETTE.length],
      backgroundColor: PALETTE[i % PALETTE.length],
      tension: 0.3,
      fill: false,
      yAxisID: 'y',
    }));

    const totalBar = {
      type: 'bar' as const,
      label: 'Total (selected)',
      data: MONTH_NAMES.map((_, mi) => selected.reduce((sum, c) => sum + monthValue(c.key, mi), 0)),
      backgroundColor: 'rgba(148, 163, 184, 0.35)',
      borderColor: 'rgba(148, 163, 184, 0.35)',
      yAxisID: 'y1',
      order: 1,
    };

    return { labels: MONTH_NAMES, datasets: [totalBar, ...lines] };
  }

  protected readonly lineOptions = computed(() => {
    const options = this.chartOptions(false) as ReturnType<Reports['chartOptions']> & {
      scales: Record<string, unknown>;
    };
    const text = cssVar('--p-text-color', '#334155');
    // Secondary axis on the right for the summed "Total" bars, kept independent of the line axis.
    options.scales['y1'] = {
      beginAtZero: true,
      position: 'right',
      ticks: { color: text },
      grid: { drawOnChartArea: false },
    };
    return options;
  });

  // ---- Chart options (themed from the current PrimeNG CSS variables) ----

  private chartOptions(beginAtZero: boolean) {
    const text = cssVar('--p-text-color', '#334155');
    const grid = cssVar('--p-content-border-color', '#e2e8f0');
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: 'top', labels: { color: text } } },
      scales: {
        x: { ticks: { color: text }, grid: { color: grid } },
        y: { beginAtZero, ticks: { color: text }, grid: { color: grid } },
      },
    };
  }
}

/** First day of the previous calendar month — the default month shown on the Monthly tab. */
function previousMonth(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth() - 1, 1);
}

/** `Date` → `YYYY-MM`. */
function monthKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/** `YYYY-MM` → first day of that month. */
function dateFromMonthKey(m: string): Date {
  const [year, month] = m.split('-').map(Number);
  return new Date(year, month - 1, 1);
}

/** `YYYY-MM` → `Mon YYYY` for display. */
function formatMonth(m: string): string {
  const [year, month] = m.split('-');
  return `${MONTH_NAMES[Number(month) - 1]} ${year}`;
}

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

const VALUE_FORMAT = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });

/**
 * Inline chart.js plugin that prints each datapoint's value next to its bar/point. Positive values
 * sit above the element, negatives below; zeros are skipped to cut clutter. Passed per-chart via the
 * PrimeNG `[plugins]` input so no global chart.js registration (or extra dependency) is needed.
 */
const valueLabelsPlugin = {
  id: 'valueLabels',
  afterDatasetsDraw(chart: {
    ctx: CanvasRenderingContext2D;
    data: { datasets: { data: (number | null)[] }[] };
    getDatasetMeta(i: number): { hidden?: boolean; data: { tooltipPosition(): { x: number; y: number } }[] };
  }) {
    const { ctx } = chart;
    ctx.save();
    ctx.font = '600 11px sans-serif';
    ctx.fillStyle = cssVar('--p-text-color', '#334155');
    ctx.textAlign = 'center';
    chart.data.datasets.forEach((dataset, di) => {
      const meta = chart.getDatasetMeta(di);
      if (meta.hidden) return;
      meta.data.forEach((element, index) => {
        const value = dataset.data[index];
        if (value == null || value === 0) return;
        const { x, y } = element.tooltipPosition();
        ctx.textBaseline = value >= 0 ? 'bottom' : 'top';
        ctx.fillText(VALUE_FORMAT.format(value), x, value >= 0 ? y - 2 : y + 2);
      });
    });
    ctx.restore();
  },
};
