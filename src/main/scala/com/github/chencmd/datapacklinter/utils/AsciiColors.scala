package com.github.chencmd.datapacklinter.utils

enum AsciiColors(private val code: Int | AsciiColors.RGBColor) {
  case Reset                         extends AsciiColors(0)
  case Bold                          extends AsciiColors(1)
  case Faint                         extends AsciiColors(2)
  case Italic                        extends AsciiColors(3)
  case Underline                     extends AsciiColors(4)
  case SlowBlank                     extends AsciiColors(5)
  case RapidBlank                    extends AsciiColors(6)
  case SwapColor                     extends AsciiColors(7)
  case F_Black                       extends AsciiColors(30)
  case F_Red                         extends AsciiColors(31)
  case F_Green                       extends AsciiColors(32)
  case F_Yellow                      extends AsciiColors(33)
  case F_Blue                        extends AsciiColors(34)
  case F_Magenta                     extends AsciiColors(35)
  case F_Cyan                        extends AsciiColors(36)
  case F_White                       extends AsciiColors(37)
  case F_BlightBlack                 extends AsciiColors(90)
  case F_BlightRed                   extends AsciiColors(91)
  case F_BlightGreen                 extends AsciiColors(92)
  case F_BlightYellow                extends AsciiColors(93)
  case F_BlightBlue                  extends AsciiColors(94)
  case F_BlightMagenta               extends AsciiColors(95)
  case F_BlightCyan                  extends AsciiColors(96)
  case F_BlightWhite                 extends AsciiColors(97)
  case F_RGB(r: Int, g: Int, b: Int) extends AsciiColors(AsciiColors.RGBColor("foreground", r, g, b))
  case F_Reset                       extends AsciiColors(39)
  case B_Black                       extends AsciiColors(40)
  case B_Red                         extends AsciiColors(41)
  case B_Green                       extends AsciiColors(42)
  case B_Yellow                      extends AsciiColors(43)
  case B_Blue                        extends AsciiColors(44)
  case B_Magenta                     extends AsciiColors(45)
  case B_Cyan                        extends AsciiColors(46)
  case B_White                       extends AsciiColors(47)
  case B_BlightBlack                 extends AsciiColors(100)
  case B_BlightRed                   extends AsciiColors(101)
  case B_BlightGreen                 extends AsciiColors(102)
  case B_BlightYellow                extends AsciiColors(103)
  case B_BlightBlue                  extends AsciiColors(104)
  case B_BlightMagenta               extends AsciiColors(105)
  case B_BlightCyan                  extends AsciiColors(106)
  case B_BlightWhite                 extends AsciiColors(107)
  case B_RGB(r: Int, g: Int, b: Int) extends AsciiColors(AsciiColors.RGBColor("background", r, g, b))
  case B_Reset                       extends AsciiColors(49)

  override def toString(): String = s"\u001b[${code}m"
}

object AsciiColors {
  protected case class RGBColor(kind: "foreground" | "background", r: Int, g: Int, b: Int) {
    override def toString(): String = kind match {
      case "foreground" => s"38;2;$r;$g;$b"
      case "background" => s"48;2;$r;$g;$b"
    }
  }
}
