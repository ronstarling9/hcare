import { Colors } from '@/constants/colors';

describe('Colors', () => {
  it('exports color-dark', () => {
    expect(Colors.dark).toBe('#1a1a24');
  });
  it('exports color-blue', () => {
    expect(Colors.blue).toBe('#1a9afa');
  });
});
