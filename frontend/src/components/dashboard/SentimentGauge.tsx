import React, { useMemo } from 'react';
import { getSentimentLabel, getSentimentColor } from '@/utils/formatters';

interface SentimentGaugeProps {
  score: number;
  size?: number;
}

const GAUGE_ZONES = [
  { label: 'Bearish', color: '#ef4444', from: 0, to: 30 },
  { label: 'Caution', color: '#f97316', from: 30, to: 50 },
  { label: 'Neutral', color: '#eab308', from: 50, to: 70 },
  { label: 'Bullish', color: '#22c55e', from: 70, to: 85 },
  { label: 'Very Bullish', color: '#10b981', from: 85, to: 100 },
];

function polarToCartesian(cx: number, cy: number, r: number, angleDeg: number) {
  const rad = ((angleDeg - 90) * Math.PI) / 180;
  return {
    x: cx + r * Math.cos(rad),
    y: cy + r * Math.sin(rad),
  };
}

function describeArc(cx: number, cy: number, r: number, startAngle: number, endAngle: number) {
  const start = polarToCartesian(cx, cy, r, endAngle);
  const end = polarToCartesian(cx, cy, r, startAngle);
  const largeArc = endAngle - startAngle <= 180 ? '0' : '1';
  return `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArc} 0 ${end.x} ${end.y}`;
}

// Map 0-100 score to -135 → +135 degrees (270° total sweep)
function scoreToAngle(score: number): number {
  return -135 + (score / 100) * 270;
}

export const SentimentGauge: React.FC<SentimentGaugeProps> = ({ score, size = 240 }) => {
  const cx = size / 2;
  const cy = size / 2;
  const outerR = size * 0.44;
  const innerR = size * 0.32;
  const needleLength = size * 0.38;

  const needleAngle = useMemo(() => scoreToAngle(Math.max(0, Math.min(100, score))), [score]);
  const needleRad = useMemo(() => ((needleAngle - 90) * Math.PI) / 180, [needleAngle]);

  const needleX = cx + needleLength * Math.cos(needleRad);
  const needleY = cy + needleLength * Math.sin(needleRad);

  const sentimentColor = getSentimentColor(score);
  const sentimentLabel = getSentimentLabel(score);

  return (
    <div className="flex flex-col items-center">
      <svg width={size} height={size * 0.72} viewBox={`0 0 ${size} ${size * 0.72}`}>
        {/* Background arcs for zones */}
        {GAUGE_ZONES.map((zone) => {
          const startAngle = -135 + (zone.from / 100) * 270;
          const endAngle = -135 + (zone.to / 100) * 270;
          return (
            <path
              key={zone.label}
              d={describeArc(cx, cy, outerR, startAngle, endAngle)}
              fill="none"
              stroke={zone.color}
              strokeWidth={size * 0.1}
              strokeOpacity={0.25}
            />
          );
        })}

        {/* Active arc up to current score */}
        {score > 0 && (
          <path
            d={describeArc(cx, cy, outerR, -135, scoreToAngle(score))}
            fill="none"
            stroke={sentimentColor}
            strokeWidth={size * 0.1}
            strokeLinecap="round"
          />
        )}

        {/* Inner ring */}
        <circle cx={cx} cy={cy} r={innerR} fill="#1c2333" stroke="#2d3748" strokeWidth={1} />

        {/* Needle */}
        <line
          x1={cx}
          y1={cy}
          x2={needleX}
          y2={needleY}
          stroke={sentimentColor}
          strokeWidth={3}
          strokeLinecap="round"
        />
        <circle cx={cx} cy={cy} r={size * 0.04} fill={sentimentColor} />

        {/* Score text */}
        <text
          x={cx}
          y={cy + size * 0.06}
          textAnchor="middle"
          fill="#f1f5f9"
          fontSize={size * 0.14}
          fontWeight="700"
          fontFamily="Inter, system-ui, sans-serif"
        >
          {Math.round(score)}
        </text>

        {/* Zone tick marks */}
        {[0, 30, 50, 70, 85, 100].map((val) => {
          const angle = scoreToAngle(val);
          const rad = ((angle - 90) * Math.PI) / 180;
          const x1 = cx + (outerR - size * 0.12) * Math.cos(rad);
          const y1 = cy + (outerR - size * 0.12) * Math.sin(rad);
          const x2 = cx + outerR * Math.cos(rad);
          const y2 = cy + outerR * Math.sin(rad);
          return (
            <line
              key={val}
              x1={x1}
              y1={y1}
              x2={x2}
              y2={y2}
              stroke="#4b5563"
              strokeWidth={2}
            />
          );
        })}
      </svg>

      {/* Label */}
      <div className="text-center mt-1">
        <div
          className="text-lg font-bold tracking-wide"
          style={{ color: sentimentColor }}
        >
          {sentimentLabel}
        </div>
        <div className="text-xs text-gray-500 mt-0.5">Market Sentiment Score</div>
      </div>

      {/* Zone legend */}
      <div className="flex flex-wrap justify-center gap-2 mt-3">
        {GAUGE_ZONES.map((zone) => (
          <div key={zone.label} className="flex items-center gap-1">
            <div
              className="w-2 h-2 rounded-full"
              style={{ backgroundColor: zone.color }}
            />
            <span className="text-xs text-gray-400">{zone.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
};

export default SentimentGauge;
